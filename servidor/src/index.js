// Servidor do Tips (Cloudflare Worker + banco D1), multiusuário.
//
// Um bot só (o do dono do servidor) atende todo mundo. Cada pessoa é a sua conversa privada
// com o bot (chat_id). Ninguém além do servidor conhece o token do bot.
//
// Conectar um celular:
//   1. App: POST /aparelhos → recebe id + chave do aparelho e um link t.me/<bot>?start=<código>.
//   2. A pessoa abre o link e toca em Iniciar; o bot recebe "/start <código>" e liga a conversa
//      dela àquele aparelho. O bot responde "Conectado".
//
// Uso:
//   - A pessoa encaminha a entrada ao bot. O servidor guarda as mensagens da conversa dela e,
//     quando chega o print do Resumo, confere se veio a entrada toda (principal + proteções).
//     Completa: manda um push (FCM) para os aparelhos dela e responde "Entrada recebida".
//     Incompleta (faltou qualquer parte): responde que está incompleta e pede para refazer (o app também a descarta).
//   - O app busca GET /updates (só as mensagens da pessoa) e GET /foto (prints dela) e anota.
//
// Segredos (npx wrangler secret put <NOME>, ou "valor" | npx wrangler secret put <NOME>):
//   TELEGRAM_TOKEN       token do bot
//   APP_SECRET           senha do dono (rotas /admin) e base do segredo do webhook
//   FCM_SERVICE_ACCOUNT  JSON da conta de serviço do Firebase

const DIAS_GUARDANDO = 7;
const VALIDADE_CODIGO_SEGUNDOS = 15 * 60;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const rota = `${request.method} ${url.pathname}`;
    try {
      // Públicas
      if (rota === "POST /telegram") return await receberDoTelegram(request, env, ctx);
      if (rota === "POST /aparelhos") return await criarAparelho(env);
      if (rota === "GET /diagnostico") return await diagnostico(env);

      // Dono do servidor
      if (url.pathname.startsWith("/admin/")) {
        if (!(await isAdmin(request, env))) return json({ ok: false, erro: "Senha do servidor inválida" }, 401);
        if (rota === "POST /admin/ativar") return await ativarWebhook(url, env);
        if (rota === "GET /admin/status") return await statusAdmin(env);
        return json({ ok: false, erro: "Não encontrado" }, 404);
      }

      // Aparelho (chave própria de cada celular)
      const aparelho = await aparelhoAutenticado(request, env);
      if (!aparelho) return json({ ok: false, erro: "Aparelho não reconhecido. Conecte de novo no app." }, 401);
      if (rota === "GET /aparelho") return await estadoAparelho(aparelho, env);
      if (rota === "POST /aparelho/codigo") return await novoCodigo(aparelho, env);
      if (rota === "POST /aparelho/fcm") return await registrarFcm(request, aparelho, env);
      if (rota === "DELETE /aparelho") return await removerAparelho(aparelho, env);
      if (rota === "GET /updates") return await listarUpdates(url, aparelho, env);
      if (rota === "GET /foto") return await foto(url, aparelho, env);
      return json({ ok: false, erro: "Não encontrado" }, 404);
    } catch (e) {
      // Nunca devolve detalhes (poderiam conter o token)
      console.error("erro", e && e.message);
      return json({ ok: false, erro: "Erro interno do servidor" }, 500);
    }
  },

  // Limpeza diária
  async scheduled(event, env, ctx) {
    const agora = agoraSegundos();
    await env.DB.prepare("DELETE FROM updates WHERE recebido_em < ?").bind(agora - DIAS_GUARDANDO * 24 * 3600).run();
    await env.DB.prepare("DELETE FROM codigos WHERE criado_em < ?").bind(agora - VALIDADE_CODIGO_SEGUNDOS).run();
    // Aparelhos que nunca terminaram a conexão
    await env.DB.prepare("DELETE FROM aparelhos WHERE chat_id IS NULL AND criado_em < ?").bind(agora - 24 * 3600).run();
  },
};

// ── Telegram → servidor ─────────────────────────────────────────────────

async function receberDoTelegram(request, env, ctx) {
  const recebido = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
  if (!(await iguais(recebido, await segredoWebhook(env)))) return json({ ok: false }, 401);

  const update = await request.json();
  const message = update.message;
  // Só conversas privadas com o bot
  if (!message || !message.chat || message.chat.type !== "private") return json({ ok: true });
  const chatId = String(message.chat.id);
  const texto = message.text || "";

  if (texto.startsWith("/start")) {
    ctx.waitUntil(conectarConversa(env, chatId, texto.split(/\s+/)[1], message.from));
    return json({ ok: true });
  }

  const conectado = await env.DB.prepare("SELECT 1 AS x FROM aparelhos WHERE chat_id = ? LIMIT 1").bind(chatId).first("x");
  if (!conectado) {
    // Quem ainda não conectou: avisa uma vez por entrada (na mensagem que fecha), sem guardar nada
    if (fechaEntrada(message)) {
      ctx.waitUntil(responder(env, chatId,
        "Para as entradas irem para o app, abra o Tips e toque em “Conectar com o Telegram”."));
    }
    return json({ ok: true });
  }

  await env.DB.prepare("INSERT OR IGNORE INTO updates (update_id, json, recebido_em, chat_id) VALUES (?, ?, ?, ?)")
    .bind(update.update_id, JSON.stringify(update), agoraSegundos(), chatId)
    .run();

  // Responde rápido ao Telegram; a conferência, o push e a resposta seguem em paralelo
  if (fechaEntrada(message)) {
    ctx.waitUntil(conferirEntrada(env, chatId, update.update_id, message));
  } else if (ehAposta(message)) {
    ctx.waitUntil(conferirSeFicouSemFechamento(env, chatId, update.update_id, message));
  }
  return json({ ok: true });
}

// Mesma regra do app (ParserOperacao.kt): a entrada termina no print do Resumo
// (link da calculadora) ou, no formato antigo, na mensagem de lucro.
export function fechaEntrada(message) {
  const texto = message.caption || message.text || "";
  return /oddsnamao|\/calculadora/i.test(texto) ||
    texto.includes("LUCRO DA OPERAÇÃO") ||
    texto.includes("Lucro Líquido");
}

// ── Entrada completa? ───────────────────────────────────────────────────
// Mesma regra do app (ParserOperacao.kt → separarOperacoes / isOperacaoCompleta).
// A pessoa encaminha a entrada toda de uma vez; as mensagens chegam no mesmo segundo.
// Uma entrada começa depois de um sticker, de uma entrada fechada ou de uma pausa de mais de
// PAUSA_NOVA_ENTRADA_SEGUNDOS, e precisa da aposta principal e de todas as proteções antes do fechamento.
// Cada envio recebe uma resposta só: "Entrada recebida" ou "Entrada incompleta".

export const PAUSA_NOVA_ENTRADA_SEGUNDOS = 4;
// Espera as mensagens encaminhadas junto chegarem (o Telegram pode entregar em paralelo)
const ESPERA_FECHAMENTO_MS = 1500;
// Sem fechamento: espera um pouco mais que a pausa antes de concluir que o envio terminou
// (as mensagens encaminhadas juntas chegam em até ~1 s)
const ESPERA_FIM_DO_ENVIO_MS = 5000;

const AVISO_INCOMPLETA =
  "⚠️ Entrada incompleta. Ela foi desconsiderada.\n\n" +
  "Encaminhe a entrada de novo, completa e de uma vez só: o sticker, a aposta principal, " +
  "todas as proteções e o Resumo (ou a mensagem do lucro).";

const TEXTO_DO_ADM = /APOSTA PRINCIPAL|PROTEÇÃO DA APOSTA/i;

// Print de aposta ou texto do adm; conversa solta ("oi", /start...) não conta
export function ehAposta(m) {
  return !m.sticker && !fechaEntrada(m) && (Boolean(m.photo) || TEXTO_DO_ADM.test(m.caption || m.text || ""));
}

// apostas: mensagens da entrada antes do fechamento
export function entradaCompleta(apostas) {
  const textos = apostas.map((m) => m.caption || m.text || "");
  // Só prints: aposta principal + pelo menos uma proteção
  if (!textos.some((t) => TEXTO_DO_ADM.test(t))) return apostas.length >= 2;

  if (!textos.some((t) => t.includes("APOSTA PRINCIPAL"))) return false;
  // "PROTEÇÃO DA APOSTA 2/3": precisam vir todas; sem número, pelo menos uma
  const numeradas = textos.map((t) => t.match(/PROTEÇÃO DA APOSTA\s*(\d+)\s*\/\s*(\d+)/i)).filter(Boolean);
  const total = Math.max(0, ...numeradas.map((m) => Number(m[2])));
  if (total > 0) {
    const vistas = new Set(numeradas.map((m) => Number(m[1])));
    for (let i = 1; i <= total; i++) if (!vistas.has(i)) return false;
    return true;
  }
  return textos.some((t) => /PROTEÇÃO DA APOSTA/i.test(t));
}

// Apostas da entrada que termina em ateUpdateId (incluído), do mais antigo para o mais novo
async function apostasDaEntrada(env, chatId, ateUpdateId) {
  const { results } = await env.DB
    .prepare("SELECT update_id, json FROM updates WHERE chat_id = ? AND update_id <= ? ORDER BY update_id DESC LIMIT 50")
    .bind(chatId, ateUpdateId)
    .all();
  const apostas = [];
  let maisNova = null;
  for (const linha of results) {
    const m = JSON.parse(linha.json).message;
    if (!m) continue;
    const ehOFim = linha.update_id === ateUpdateId;
    if (!ehOFim && (m.sticker || fechaEntrada(m))) break;
    if (maisNova && m.date && maisNova - m.date > PAUSA_NOVA_ENTRADA_SEGUNDOS) break;
    maisNova = m.date || maisNova;
    if (ehAposta(m)) apostas.unshift(m);
  }
  return apostas;
}

function dormir(env, ms) {
  return env.DORMIR ? env.DORMIR(ms) : new Promise((r) => setTimeout(r, ms));
}

// Chegou o fechamento: confere a entrada e confirma ou pede para refazer
async function conferirEntrada(env, chatId, updateId, message) {
  await dormir(env, ESPERA_FECHAMENTO_MS);
  if (entradaCompleta(await apostasDaEntrada(env, chatId, updateId))) {
    await Promise.all([
      enviarPush(env, chatId),
      responder(env, chatId, "📥 Entrada recebida! Ela vai aparecer no Tips em instantes.", message.message_id),
    ]);
  } else {
    await responder(env, chatId, AVISO_INCOMPLETA, message.message_id);
  }
}

// Chegou uma aposta: se o envio terminar sem o fechamento, a entrada ficou incompleta
async function conferirSeFicouSemFechamento(env, chatId, updateId, message) {
  await dormir(env, ESPERA_FIM_DO_ENVIO_MS);
  const seguinte = await env.DB
    .prepare("SELECT json FROM updates WHERE chat_id = ? AND update_id > ? ORDER BY update_id LIMIT 1")
    .bind(chatId, updateId)
    .first("json");
  const proxima = seguinte && JSON.parse(seguinte).message;
  // Chegou mais coisa da mesma entrada: quem conferir é a última mensagem dela
  if (proxima && !proxima.sticker &&
    !(proxima.date && message.date && proxima.date - message.date > PAUSA_NOVA_ENTRADA_SEGUNDOS)) return;
  await responder(env, chatId, AVISO_INCOMPLETA, message.message_id);
}

async function conectarConversa(env, chatId, codigo, de) {
  if (!codigo) {
    await responder(env, chatId,
      "Olá! Para usar, abra o app Tips e toque em “Conectar com o Telegram”. " +
      "Depois é só encaminhar as entradas para mim.");
    return;
  }
  const registro = await env.DB.prepare("SELECT aparelho_id, criado_em FROM codigos WHERE codigo = ?")
    .bind(limparChave(codigo)).first();
  if (!registro || agoraSegundos() - registro.criado_em > VALIDADE_CODIGO_SEGUNDOS) {
    await responder(env, chatId, "Esse link de conexão expirou. No app Tips, toque em “Conectar com o Telegram” de novo.");
    return;
  }
  const nome = [de && de.first_name, de && de.last_name].filter(Boolean).join(" ") || null;
  await env.DB.prepare("UPDATE aparelhos SET chat_id = ?, nome = ? WHERE id = ?").bind(chatId, nome, registro.aparelho_id).run();
  await env.DB.prepare("DELETE FROM codigos WHERE codigo = ?").bind(limparChave(codigo)).run();
  await responder(env, chatId,
    "✅ Conectado ao Tips!\n\nAgora é só encaminhar para mim as mensagens de cada entrada " +
    "(sticker, prints com os links e o print do Resumo). A surebet é anotada no app assim que o Resumo chegar.\n\n" +
    `As mensagens ficam guardadas no servidor por até ${DIAS_GUARDANDO} dias, só até o app buscar.`);
}

// ── App → servidor ──────────────────────────────────────────────────────

// Novo aparelho: devolve a chave (só esta vez) e o link de conexão com o Telegram
async function criarAparelho(env) {
  const id = aleatorio(16);
  const chave = aleatorio(32);
  await env.DB.prepare("INSERT INTO aparelhos (id, chave_hash, criado_em) VALUES (?, ?, ?)")
    .bind(id, await hashHex(chave), agoraSegundos())
    .run();
  const { codigo, link } = await criarCodigo(env, id);
  return json({ ok: true, id, chave, codigo, link });
}

async function novoCodigo(aparelho, env) {
  const { codigo, link } = await criarCodigo(env, aparelho.id);
  return json({ ok: true, codigo, link });
}

async function criarCodigo(env, aparelhoId) {
  // Sem letras/números parecidos (0/O, 1/I/L)
  const alfabeto = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(10));
  const codigo = [...bytes].map((b) => alfabeto[b % alfabeto.length]).join("");
  await env.DB.prepare("INSERT INTO codigos (codigo, aparelho_id, criado_em) VALUES (?, ?, ?)")
    .bind(codigo, aparelhoId, agoraSegundos())
    .run();
  return { codigo, link: `https://t.me/${await nomeDoBot(env)}?start=${codigo}` };
}

async function estadoAparelho(aparelho, env) {
  await env.DB.prepare("UPDATE aparelhos SET visto_em = ? WHERE id = ?").bind(agoraSegundos(), aparelho.id).run();
  return json({
    ok: true,
    conectado: Boolean(aparelho.chat_id),
    nome: aparelho.nome || null,
    bot: `@${await nomeDoBot(env)}`,
    push: Boolean(aparelho.fcm_token),
  });
}

async function registrarFcm(request, aparelho, env) {
  const corpo = await request.json().catch(() => ({}));
  const token = typeof corpo.token === "string" ? corpo.token.trim() : "";
  if (!token || token.length > 4096) return json({ ok: false, erro: "Token de push inválido" }, 400);
  await env.DB.prepare("UPDATE aparelhos SET fcm_token = ? WHERE id = ?").bind(token, aparelho.id).run();
  return json({ ok: true });
}

async function removerAparelho(aparelho, env) {
  await env.DB.prepare("DELETE FROM aparelhos WHERE id = ?").bind(aparelho.id).run();
  await env.DB.prepare("DELETE FROM codigos WHERE aparelho_id = ?").bind(aparelho.id).run();
  return json({ ok: true });
}

// GET /updates?after=N: mensagens da conversa do aparelho depois de N (mesmo formato do getUpdates).
// Nada é apagado aqui: cada aparelho guarda o próprio N; a limpeza é diária (7 dias).
async function listarUpdates(url, aparelho, env) {
  if (!aparelho.chat_id) return json({ ok: false, erro: "Aparelho ainda não conectado ao Telegram." }, 409);
  const after = Number.parseInt(url.searchParams.get("after") || "0", 10) || 0;
  const limite = Math.min(Number.parseInt(url.searchParams.get("limit") || "100", 10) || 100, 100);
  const { results } = await env.DB
    .prepare("SELECT json FROM updates WHERE chat_id = ? AND update_id > ? ORDER BY update_id LIMIT ?")
    .bind(aparelho.chat_id, after, limite)
    .all();
  return json({ ok: true, result: results.map((r) => JSON.parse(r.json)) });
}

// GET /foto?file_id=X: baixa o print do Telegram, só se ele estiver numa mensagem da própria pessoa
async function foto(url, aparelho, env) {
  const fileId = url.searchParams.get("file_id") || "";
  if (!aparelho.chat_id || !/^[A-Za-z0-9_-]{10,300}$/.test(fileId)) return json({ ok: false, erro: "Foto inválida" }, 400);
  const dela = await env.DB
    .prepare("SELECT 1 AS x FROM updates WHERE chat_id = ? AND instr(json, ?) > 0 LIMIT 1")
    .bind(aparelho.chat_id, `"file_id":"${fileId}"`)
    .first("x");
  if (!dela) return json({ ok: false, erro: "Foto não encontrada" }, 404);

  const info = await chamarTelegram(env, "getFile", { file_id: fileId });
  if (!info.ok) return json({ ok: false, erro: "O Telegram não entregou a foto" }, 502);
  const arquivo = await fetch(`https://api.telegram.org/file/bot${limparChave(env.TELEGRAM_TOKEN)}/${info.result.file_path}`);
  if (!arquivo.ok) return json({ ok: false, erro: "O Telegram não entregou a foto" }, 502);
  return new Response(arquivo.body, {
    headers: { "Content-Type": arquivo.headers.get("Content-Type") || "application/octet-stream" },
  });
}

// ── Dono do servidor ────────────────────────────────────────────────────

// Liga o webhook do bot apontando para este servidor
async function ativarWebhook(url, env) {
  const resposta = await chamarTelegram(env, "setWebhook", {
    url: `${url.origin}/telegram`,
    secret_token: await segredoWebhook(env),
    allowed_updates: ["message"],
    drop_pending_updates: false,
  });
  return json({ ok: resposta.ok === true, erro: resposta.ok ? undefined : resposta.description });
}

async function statusAdmin(env) {
  const info = await chamarTelegram(env, "getWebhookInfo", {});
  const contar = (sql) => env.DB.prepare(sql).first("n");
  const r = info.result || {};
  return json({
    ok: info.ok === true,
    webhook_ativo: Boolean(r.url),
    ultimo_erro_telegram: r.last_error_message || null,
    usuarios: await contar("SELECT COUNT(DISTINCT chat_id) AS n FROM aparelhos WHERE chat_id IS NOT NULL"),
    aparelhos_conectados: await contar("SELECT COUNT(*) AS n FROM aparelhos WHERE chat_id IS NOT NULL"),
    mensagens_guardadas: await contar("SELECT COUNT(*) AS n FROM updates"),
  });
}

// Diagnóstico sem expor segredos: só diz se cada um existe e se parece válido.
async function diagnostico(env) {
  const bruta = String(env.APP_SECRET || "");
  const limpa = limparChave(bruta);
  let bot = null;
  if (env.TELEGRAM_TOKEN) {
    const me = await chamarTelegram(env, "getMe", {}).catch(() => ({ ok: false }));
    bot = me.ok ? `@${me.result.username}` : `inválido (${me.description || "sem resposta"})`;
  }
  let firebase = null;
  if (env.FCM_SERVICE_ACCOUNT) {
    try {
      const conta = JSON.parse(env.FCM_SERVICE_ACCOUNT);
      firebase = conta.private_key && conta.client_email ? `ok (projeto ${conta.project_id})` : "JSON sem a chave privada";
    } catch {
      firebase = "não é um JSON válido";
    }
  }
  return json({
    app_secret: bruta ? { tamanho: limpa.length, caracteres_removidos_ao_limpar: bruta.length - limpa.length } : "não cadastrada",
    telegram_bot: bot ?? "TELEGRAM_TOKEN não cadastrado",
    firebase: firebase ?? "FCM_SERVICE_ACCOUNT não cadastrada",
  });
}

// ── Push (Firebase Cloud Messaging, API HTTP v1) ────────────────────────

async function enviarPush(env, chatId) {
  if (!env.FCM_SERVICE_ACCOUNT) return;
  const { results } = await env.DB
    .prepare("SELECT id, fcm_token FROM aparelhos WHERE chat_id = ? AND fcm_token IS NOT NULL")
    .bind(chatId)
    .all();
  if (results.length === 0) return;
  const conta = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const acesso = await tokenDeAcessoFcm(env, conta);

  for (const { id, fcm_token: token } of results) {
    const resposta = await fetch(`https://fcm.googleapis.com/v1/projects/${conta.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${acesso}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          // Mensagem só de dados, prioridade alta: acorda o app mesmo fechado
          data: { tipo: "nova_entrada" },
          android: { priority: "HIGH", ttl: "3600s" },
        },
      }),
    });
    if (resposta.status === 404 || resposta.status === 400) {
      const corpo = await resposta.text();
      // App desinstalado / token trocado: para de mandar até o app registrar outro
      if (corpo.includes("UNREGISTERED") || corpo.includes("registration-token-not-registered")) {
        await env.DB.prepare("UPDATE aparelhos SET fcm_token = NULL WHERE id = ?").bind(id).run();
      }
    }
  }
}

// Token OAuth da conta de serviço, guardado no D1 até perto de expirar (~1 h)
async function tokenDeAcessoFcm(env, conta) {
  const agora = agoraSegundos();
  const cache = await env.DB.prepare("SELECT valor FROM config WHERE chave = 'fcm_token'").first("valor");
  if (cache) {
    const { token, expira } = JSON.parse(cache);
    if (expira - 300 > agora) return token;
  }

  const cabecalho = { alg: "RS256", typ: "JWT" };
  const declaracoes = {
    iss: conta.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: agora,
    exp: agora + 3600,
  };
  const semAssinatura = `${base64Url(JSON.stringify(cabecalho))}.${base64Url(JSON.stringify(declaracoes))}`;
  const chave = await crypto.subtle.importKey(
    "pkcs8",
    pemParaBytes(conta.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const assinatura = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", chave, new TextEncoder().encode(semAssinatura));
  const jwt = `${semAssinatura}.${base64UrlBytes(new Uint8Array(assinatura))}`;

  const resposta = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  const dados = await resposta.json();
  if (!dados.access_token) throw new Error("Falha ao obter token do Firebase");

  await env.DB.prepare("INSERT OR REPLACE INTO config (chave, valor) VALUES ('fcm_token', ?)")
    .bind(JSON.stringify({ token: dados.access_token, expira: agora + (dados.expires_in || 3600) }))
    .run();
  return dados.access_token;
}

// ── Utilitários ─────────────────────────────────────────────────────────

async function chamarTelegram(env, metodo, corpo) {
  // limparChave: o token pode ter vindo com quebra de linha ao ser cadastrado
  const resposta = await fetch(`https://api.telegram.org/bot${limparChave(env.TELEGRAM_TOKEN)}/${metodo}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  return resposta.json();
}

function responder(env, chatId, texto, responderA) {
  return chamarTelegram(env, "sendMessage", {
    chat_id: chatId,
    text: texto,
    ...(responderA ? { reply_parameters: { message_id: responderA, allow_sending_without_reply: true } } : {}),
  });
}

// @ do bot (para o link t.me), guardado em cache
async function nomeDoBot(env) {
  const cache = await env.DB.prepare("SELECT valor FROM config WHERE chave = 'bot_username'").first("valor");
  if (cache) return cache;
  const me = await chamarTelegram(env, "getMe", {});
  if (!me.ok) throw new Error("Token do bot inválido");
  await env.DB.prepare("INSERT OR REPLACE INTO config (chave, valor) VALUES ('bot_username', ?)").bind(me.result.username).run();
  return me.result.username;
}

// Authorization: Bearer <id>.<chave>
async function aparelhoAutenticado(request, env) {
  const valor = limparChave((request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, ""));
  const [id, chave] = valor.split(".");
  if (!id || !chave) return null;
  const aparelho = await env.DB.prepare("SELECT id, chave_hash, chat_id, nome, fcm_token FROM aparelhos WHERE id = ?").bind(id).first();
  if (!aparelho) return null;
  return (await iguais(await hashHex(chave), aparelho.chave_hash)) ? aparelho : null;
}

async function isAdmin(request, env) {
  const segredo = limparChave(env.APP_SECRET);
  if (!segredo) return false;
  const cabecalho = request.headers.get("Authorization") || "";
  return iguais(limparChave(cabecalho.replace(/^Bearer\s+/i, "")), segredo);
}

// Remove espaços, quebras de linha, aspas e caracteres invisíveis que vêm junto ao colar
export function limparChave(chave) {
  // \p{C}: caracteres de controle e invisíveis (ex.: ^V que alguns terminais inserem ao colar)
  return String(chave || "").replace(/[\s\p{C}"'`]/gu, "");
}

// Segredo que o Telegram manda em cada webhook; derivado da APP_SECRET
async function segredoWebhook(env) {
  return hashHex(`${limparChave(env.APP_SECRET)}:webhook`);
}

async function hashHex(texto) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function aleatorio(bytes) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Comparação em tempo constante (compara os hashes, que têm o mesmo tamanho)
async function iguais(a, b) {
  const [ha, hb] = await Promise.all([
    crypto.subtle.digest("SHA-256", new TextEncoder().encode(a)),
    crypto.subtle.digest("SHA-256", new TextEncoder().encode(b)),
  ]);
  const x = new Uint8Array(ha);
  const y = new Uint8Array(hb);
  let diferenca = 0;
  for (let i = 0; i < x.length; i++) diferenca |= x[i] ^ y[i];
  return diferenca === 0 && a.length > 0;
}

function pemParaBytes(pem) {
  const base64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function base64Url(texto) {
  return base64UrlBytes(new TextEncoder().encode(texto));
}

function base64UrlBytes(bytes) {
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function agoraSegundos() {
  return Math.floor(Date.now() / 1000);
}

function json(corpo, status = 200) {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
