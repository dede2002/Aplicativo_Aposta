// Testes do servidor sem Cloudflare: SQLite de verdade (node:sqlite, com a mesma API do D1)
// e fetch falso para Telegram, Google OAuth e Firebase. Rodar: npm test
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { generateKeyPairSync, createVerify, createHash } from "node:crypto";
import servidor, { fechaEntrada, limparChave, entradaCompleta } from "../src/index.js";

// ── D1 em cima do SQLite do Node ─────────────────────────────────────────
function d1(sqlite) {
  return {
    prepare(sql) {
      let args = [];
      const stmt = {
        bind: (...a) => { args = a; return stmt; },
        run: async () => { sqlite.prepare(sql).run(...args); return {}; },
        all: async () => ({ results: sqlite.prepare(sql).all(...args) }),
        first: async (coluna) => {
          const linha = sqlite.prepare(sql).get(...args);
          if (!linha) return null;
          return coluna ? (linha[coluna] ?? null) : linha;
        },
      };
      return stmt;
    },
    sqlite,
  };
}

function bancoNovo() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  return d1(sqlite);
}

// ── Telegram / Google / Firebase falsos ─────────────────────────────────
let chamadas;
function instalarFetchFalso() {
  chamadas = [];
  globalThis.fetch = async (url, opcoes = {}) => {
    const u = String(url);
    const corpo = opcoes.body && typeof opcoes.body === "string" && opcoes.body.startsWith("{") ? JSON.parse(opcoes.body) : opcoes.body;
    chamadas.push({ url: u, corpo, opcoes });
    const r = (obj, status = 200) => new Response(JSON.stringify(obj), { status });
    if (u.includes("/getMe")) return r({ ok: true, result: { username: "MinhasTips_bot" } });
    if (u.includes("/sendMessage")) return r({ ok: true, result: {} });
    if (u.includes("/getFile")) return r({ ok: true, result: { file_path: `photos/${corpo.file_id}.jpg` } });
    if (u.includes("api.telegram.org/file/")) return new Response("BYTES-DA-FOTO", { headers: { "Content-Type": "image/jpeg" } });
    if (u.includes("/setWebhook")) return r({ ok: true, result: true });
    if (u.includes("/getWebhookInfo")) return r({ ok: true, result: { url: "https://x/telegram" } });
    if (u.startsWith("https://oauth2.googleapis.com/token")) return r({ access_token: "acesso-123", expires_in: 3600 });
    if (u.startsWith("https://fcm.googleapis.com/")) return r({ name: "ok" });
    throw new Error("fetch não previsto: " + u);
  };
}
const mensagensDoBot = () => chamadas.filter((c) => c.url.includes("/sendMessage")).map((c) => c.corpo);
const pushes = () => chamadas.filter((c) => c.url.startsWith("https://fcm.googleapis.com/")).map((c) => c.corpo.message.token);

const { privateKey, publicKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
  publicKeyEncoding: { type: "spki", format: "pem" },
});

let env;
let dormindo;
let tarefasPendentes;
beforeEach(() => {
  dormindo = [];
  tarefasPendentes = [];
  instalarFetchFalso();
  env = {
    DB: bancoNovo(),
    APP_SECRET: "senha-do-dono",
    // As esperas do servidor só terminam quando o teste chama fimDoEnvio()
    DORMIR: () => new Promise((r) => dormindo.push(r)),
    TELEGRAM_TOKEN: "000000:token-falso",
    FCM_SERVICE_ACCOUNT: JSON.stringify({
      project_id: "projeto-teste",
      client_email: "teste@projeto-teste.iam.gserviceaccount.com",
      private_key: privateKey,
    }),
  };
});

const segredoWebhook = createHash("sha256").update("senha-do-dono:webhook").digest("hex");

async function chamar(metodo, caminho, { auth, corpo, cabecalhos = {} } = {}) {
  const tarefas = [];
  const resposta = await servidor.fetch(
    new Request("https://tips.exemplo.dev" + caminho, {
      method: metodo,
      headers: { "Content-Type": "application/json", ...(auth ? { Authorization: `Bearer ${auth}` } : {}), ...cabecalhos },
      body: corpo ? JSON.stringify(corpo) : undefined,
    }),
    env,
    { waitUntil: (p) => tarefas.push(p) },
  );
  // Tarefas que não esperam terminam já; as que esperam o fim do envio ficam para fimDoEnvio()
  tarefasPendentes.push(...tarefas);
  await Promise.race([Promise.all(tarefas), new Promise((r) => setTimeout(r, 50))]);
  return resposta;
}

// Simula o tempo passando depois do envio: libera as esperas e aguarda as conferências
async function fimDoEnvio() {
  while (dormindo.length || tarefasPendentes.length) {
    dormindo.splice(0).forEach((r) => r());
    await Promise.all(tarefasPendentes.splice(0));
  }
}

let proximoUpdate = 1000;
let relogio = 1_800_000_000;
function mensagemDoTelegram(chatId, extra, segredo = segredoWebhook) {
  const id = proximoUpdate++;
  relogio += 2;
  return chamar("POST", "/telegram", {
    corpo: { update_id: id, message: { message_id: id, date: relogio, chat: { id: chatId, type: "private" }, from: { first_name: "Ana" }, ...extra } },
    cabecalhos: { "X-Telegram-Bot-Api-Secret-Token": segredo },
  });
}

// Cria um aparelho, conecta à conversa chatId e registra o push
async function aparelhoConectado(chatId, fcm) {
  const novo = await (await chamar("POST", "/aparelhos")).json();
  await mensagemDoTelegram(chatId, { text: `/start ${novo.codigo}` });
  const auth = `${novo.id}.${novo.chave}`;
  if (fcm) await chamar("POST", "/aparelho/fcm", { auth, corpo: { token: fcm } });
  return auth;
}

const resumo = { photo: [{ file_id: "foto-resumo-aaaaaaaa" }], caption: "https://oddsnamao.com/calculadora/lucroseguro?calc=x" };
const sticker = { sticker: { file_unique_id: "s" } };
const printAposta = (casa) => ({ photo: [{ file_id: `foto-${casa}-aaaaaaaa` }], caption: `https://${casa}.bet.br/x` });

// Entrada completa só com prints: sticker, principal, proteção, Resumo
async function enviarEntradaCompleta(chatId) {
  await mensagemDoTelegram(chatId, sticker);
  await mensagemDoTelegram(chatId, printAposta("cassino"));
  await mensagemDoTelegram(chatId, printAposta("betbra"));
  await mensagemDoTelegram(chatId, resumo);
  await fimDoEnvio();
}

// ── Testes ──────────────────────────────────────────────────────────────

test("fechaEntrada e limparChave", () => {
  assert.equal(fechaEntrada({ caption: "https://oddsnamao.com/calculadora/lucroseguro?calc=x" }), true);
  assert.equal(fechaEntrada({ caption: "https://bettracker.com.br/calculator/simplesgreen?share=499c1ba3575d" }), true);
  assert.equal(fechaEntrada({ text: "LUCRO DA OPERAÇÃO\nLucro Líquido: R$ 10" }), true);
  assert.equal(fechaEntrada({ caption: "https://cassino.bet.br/x" }), false);
  assert.equal(limparChave(' "abc"\r\n​\u0016'), "abc");
});

test("conectar: link com código, /start liga a conversa e o bot confirma", async () => {
  const novo = await (await chamar("POST", "/aparelhos")).json();
  assert.match(novo.link, /^https:\/\/t\.me\/MinhasTips_bot\?start=[A-Z2-9]{10}$/);
  const auth = `${novo.id}.${novo.chave}`;
  assert.equal((await (await chamar("GET", "/aparelho", { auth })).json()).conectado, false);

  await mensagemDoTelegram(555, { text: `/start ${novo.codigo}` });

  const estado = await (await chamar("GET", "/aparelho", { auth })).json();
  assert.equal(estado.conectado, true);
  assert.equal(estado.nome, "Ana");
  assert.match(mensagensDoBot().at(-1).text, /Conectado ao Tips/);
  // Código é de uso único
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM codigos").get().n, 0);
});

test("código expirado não conecta", async () => {
  const novo = await (await chamar("POST", "/aparelhos")).json();
  env.DB.sqlite.prepare("UPDATE codigos SET criado_em = criado_em - 3600").run();
  await mensagemDoTelegram(555, { text: `/start ${novo.codigo}` });
  assert.match(mensagensDoBot().at(-1).text, /expirou/);
  const estado = await (await chamar("GET", "/aparelho", { auth: `${novo.id}.${novo.chave}` })).json();
  assert.equal(estado.conectado, false);
});

test("quem não conectou recebe aviso e nada é guardado", async () => {
  await mensagemDoTelegram(777, resumo);
  await fimDoEnvio();
  assert.match(mensagensDoBot().at(-1).text, /Conectar com o Telegram/);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM updates").get().n, 0);
});

test("grupo, segredo errado e chave errada são recusados", async () => {
  assert.equal((await mensagemDoTelegram(555, { text: "x" }, "errado")).status, 401);
  await chamar("POST", "/telegram", {
    corpo: { update_id: 1, message: { message_id: 1, chat: { id: -100, type: "group" }, text: "/start ABC" } },
    cabecalhos: { "X-Telegram-Bot-Api-Secret-Token": segredoWebhook },
  });
  assert.equal(mensagensDoBot().length, 0);
  const auth = await aparelhoConectado(555);
  const [id] = auth.split(".");
  assert.equal((await chamar("GET", "/updates", { auth: `${id}.chave-errada` })).status, 401);
  assert.equal((await chamar("GET", "/admin/status", { auth })).status, 401, "chave de aparelho não abre o admin");
  assert.equal((await chamar("GET", "/admin/status", { auth: "senha-do-dono" })).status, 200);
});

test("cada usuário só vê e recebe push das próprias entradas; vários aparelhos por usuário", async () => {
  const anaCelular = await aparelhoConectado(555, "fcm-ana-celular");
  const anaTablet = await aparelhoConectado(555, "fcm-ana-tablet");
  const bruno = await aparelhoConectado(888, "fcm-bruno");
  chamadas.length = 0;

  await mensagemDoTelegram(555, sticker);
  await mensagemDoTelegram(555, printAposta("cassino"));
  await mensagemDoTelegram(555, printAposta("betbra"));
  assert.equal(pushes().length, 0, "sem push antes do Resumo");
  await mensagemDoTelegram(555, resumo);
  await fimDoEnvio();

  assert.deepEqual(pushes().sort(), ["fcm-ana-celular", "fcm-ana-tablet"]);
  assert.match(mensagensDoBot().at(-1).text, /Entrada recebida/);
  assert.equal(mensagensDoBot().at(-1).chat_id, "555");

  const daAna = await (await chamar("GET", "/updates?after=0", { auth: anaCelular })).json();
  assert.equal(daAna.result.length, 4);
  // O tablet também recebe tudo (buscar não apaga)
  const doTablet = await (await chamar("GET", "/updates?after=0", { auth: anaTablet })).json();
  assert.equal(doTablet.result.length, 4);
  // Cursor: depois do 3º, só o Resumo
  const depois = await (await chamar("GET", `/updates?after=${daAna.result[2].update_id}`, { auth: anaCelular })).json();
  assert.equal(depois.result.length, 1);
  // Bruno não vê nada da Ana
  assert.equal((await (await chamar("GET", "/updates?after=0", { auth: bruno })).json()).result.length, 0);
});

test("foto: só os prints da própria pessoa", async () => {
  const ana = await aparelhoConectado(555);
  const bruno = await aparelhoConectado(888);
  await mensagemDoTelegram(555, resumo);

  const minha = await chamar("GET", "/foto?file_id=foto-resumo-aaaaaaaa", { auth: ana });
  assert.equal(minha.status, 200);
  assert.equal(await minha.text(), "BYTES-DA-FOTO");
  assert.equal((await chamar("GET", "/foto?file_id=foto-resumo-aaaaaaaa", { auth: bruno })).status, 404);
});

test("push assinado com a chave do Firebase e token em cache", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await enviarEntradaCompleta(555);
  await enviarEntradaCompleta(555);

  const oauth = chamadas.filter((c) => c.url.startsWith("https://oauth2"));
  assert.equal(oauth.length, 1, "token OAuth reaproveitado");
  const assertion = new URLSearchParams(String(oauth[0].opcoes.body)).get("assertion");
  const [cab, decl, assinatura] = assertion.split(".");
  const verificador = createVerify("RSA-SHA256");
  verificador.update(`${cab}.${decl}`);
  assert.equal(verificador.verify(publicKey, Buffer.from(assinatura, "base64url")), true);
  const push = chamadas.find((c) => c.url.startsWith("https://fcm.googleapis.com/"));
  assert.equal(push.corpo.message.android.priority, "HIGH");
  assert.deepEqual(push.corpo.message.data, { tipo: "nova_entrada" });
});

test("desconectar remove o aparelho", async () => {
  const auth = await aparelhoConectado(555);
  assert.equal((await chamar("DELETE", "/aparelho", { auth })).status, 200);
  assert.equal((await chamar("GET", "/aparelho", { auth })).status, 401);
});

test("migração do banco de um usuário só para multiusuário", () => {
  const sqlite = new DatabaseSync(":memory:");
  // Esquema da versão 1
  sqlite.exec(`
    CREATE TABLE updates (update_id INTEGER PRIMARY KEY, json TEXT NOT NULL, recebido_em INTEGER NOT NULL);
    CREATE TABLE dispositivos (token TEXT PRIMARY KEY, registrado_em INTEGER NOT NULL);
    CREATE TABLE config (chave TEXT PRIMARY KEY, valor TEXT NOT NULL);
  `);
  sqlite.prepare("INSERT INTO updates VALUES (1, ?, 0)").run(JSON.stringify({ update_id: 1, message: { chat: { id: 123456789 } } }));
  sqlite.exec(readFileSync(new URL("../migracoes/0002_multiusuario.sql", import.meta.url), "utf8"));

  assert.equal(sqlite.prepare("SELECT chat_id FROM updates WHERE update_id = 1").get().chat_id, "123456789");
  const tabelas = sqlite.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").all().map((t) => t.name);
  assert.deepEqual(tabelas, ["aparelhos", "codigos", "config", "updates"]);
});

// ── Entrada incompleta ──────────────────────────────────────────────────

const textoAdm = (titulo, casa) => ({
  photo: [{ file_id: `foto-${casa.replace(" ", "")}-aaaaaaaa` }],
  caption: `🛡 ${titulo} 🛡\n\n🏆 Partida: Austrália vs Brasil\n🏦 Casa: ${casa}\n\nLink`,
});
const lucroTexto = { photo: [{ file_id: "foto-resumo-bbbbbbbb" }], caption: "📊 LUCRO DA OPERAÇÃO 📊\n\n💰 Lucro Líquido: R$60,44" };

test("entradaCompleta: principal, proteções numeradas e só prints", () => {
  const t = (s) => ({ caption: s });
  assert.equal(entradaCompleta([t("APOSTA PRINCIPAL"), t("PROTEÇÃO DA APOSTA 1/2"), t("PROTEÇÃO DA APOSTA 2/2")]), true);
  assert.equal(entradaCompleta([t("APOSTA PRINCIPAL"), t("PROTEÇÃO DA APOSTA 2/2")]), false);
  assert.equal(entradaCompleta([t("PROTEÇÃO DA APOSTA 1/1")]), false);
  assert.equal(entradaCompleta([t("APOSTA PRINCIPAL")]), false);
  // Formato antigo, sem número
  assert.equal(entradaCompleta([t("APOSTA PRINCIPAL"), t("PROTEÇÃO DA APOSTA")]), true);
  assert.equal(entradaCompleta([{ photo: [{}] }]), false);
  assert.equal(entradaCompleta([{ photo: [{}] }, { photo: [{}] }]), true);
  assert.equal(entradaCompleta([]), false);
});

const AVISO = /Entrada incompleta\. Ela foi desconsiderada/;

test("entrada com texto do adm completa: só a confirmação", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await mensagemDoTelegram(555, sticker);
  await mensagemDoTelegram(555, textoAdm("APOSTA PRINCIPAL", "Superbet"));
  await mensagemDoTelegram(555, textoAdm("PROTEÇÃO DA APOSTA 1/2", "Sporty bet"));
  await mensagemDoTelegram(555, textoAdm("PROTEÇÃO DA APOSTA 2/2", "Betbra"));
  await mensagemDoTelegram(555, lucroTexto);
  await fimDoEnvio();
  assert.equal(pushes().length, 1);
  assert.equal(mensagensDoBot().length, 1);
  assert.match(mensagensDoBot()[0].text, /Entrada recebida/);
});

test("faltou proteção: sem push e um aviso só", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await mensagemDoTelegram(555, sticker);
  await mensagemDoTelegram(555, textoAdm("APOSTA PRINCIPAL", "Superbet"));
  await mensagemDoTelegram(555, textoAdm("PROTEÇÃO DA APOSTA 2/2", "Betbra"));
  await mensagemDoTelegram(555, lucroTexto);
  await fimDoEnvio();
  assert.equal(pushes().length, 0);
  assert.equal(mensagensDoBot().length, 1);
  assert.match(mensagensDoBot()[0].text, AVISO);

  // Reenviada completa: só a confirmação, sem aviso da anterior
  chamadas.length = 0;
  await enviarEntradaCompleta(555);
  assert.equal(pushes().length, 1);
  assert.equal(mensagensDoBot().length, 1);
  assert.match(mensagensDoBot()[0].text, /Entrada recebida/);
});

test("só o Resumo ou um print só: incompleta", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await mensagemDoTelegram(555, resumo);
  await fimDoEnvio();
  await mensagemDoTelegram(555, sticker);
  await mensagemDoTelegram(555, printAposta("cassino"));
  await mensagemDoTelegram(555, resumo);
  await fimDoEnvio();
  assert.equal(mensagensDoBot().length, 2);
  assert.ok(mensagensDoBot().every((m) => AVISO.test(m.text)));
  assert.equal(pushes().length, 0);
});

test("sem o Resumo: avisa logo depois do envio, uma vez, e a próxima entrada só confirma", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await mensagemDoTelegram(555, sticker);
  await mensagemDoTelegram(555, printAposta("cassino"));
  await mensagemDoTelegram(555, printAposta("betbra"));
  await fimDoEnvio();
  assert.equal(mensagensDoBot().length, 1);
  assert.match(mensagensDoBot()[0].text, AVISO);

  chamadas.length = 0;
  await enviarEntradaCompleta(555);
  assert.equal(mensagensDoBot().length, 1);
  assert.match(mensagensDoBot()[0].text, /Entrada recebida/);
  assert.equal(pushes().length, 1);
});

test("pausa longa separa entradas; conversa solta não gera aviso", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  await mensagemDoTelegram(555, printAposta("cassino"));
  relogio += 60;
  // Sem sticker, um minuto depois: a aposta antiga não entra nesta
  await mensagemDoTelegram(555, printAposta("betbra"));
  await mensagemDoTelegram(555, resumo);
  await fimDoEnvio();
  assert.equal(mensagensDoBot().length, 2);
  assert.ok(mensagensDoBot().every((m) => AVISO.test(m.text)));

  chamadas.length = 0;
  await mensagemDoTelegram(555, { text: "oi" });
  await mensagemDoTelegram(555, sticker);
  await fimDoEnvio();
  assert.equal(mensagensDoBot().length, 0);
});

test("duas entradas encaminhadas de uma vez: duas confirmações, nenhum aviso", async () => {
  await aparelhoConectado(555, "fcm-ana");
  chamadas.length = 0;
  for (const m of [sticker, printAposta("cassino"), printAposta("betbra"), resumo,
    sticker, printAposta("superbet"), printAposta("betano"), resumo]) {
    await mensagemDoTelegram(555, m);
  }
  await fimDoEnvio();
  assert.equal(pushes().length, 2);
  assert.equal(mensagensDoBot().length, 2);
  assert.ok(mensagensDoBot().every((m) => /Entrada recebida/.test(m.text)));
});
