// Testes do servidor sem Cloudflare: SQLite de verdade (node:sqlite, com a mesma API do D1)
// e fetch falso para Telegram, Google OAuth e Firebase. Rodar: npm test
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { generateKeyPairSync, createVerify, createHash } from "node:crypto";
import servidor, { fechaEntrada, limparChave } from "../src/index.js";

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
beforeEach(() => {
  instalarFetchFalso();
  env = {
    DB: bancoNovo(),
    APP_SECRET: "senha-do-dono",
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
  await Promise.all(tarefas);
  return resposta;
}

let proximoUpdate = 1000;
function mensagemDoTelegram(chatId, extra, segredo = segredoWebhook) {
  const id = proximoUpdate++;
  return chamar("POST", "/telegram", {
    corpo: { update_id: id, message: { message_id: id, chat: { id: chatId, type: "private" }, from: { first_name: "Ana" }, ...extra } },
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

// ── Testes ──────────────────────────────────────────────────────────────

test("fechaEntrada e limparChave", () => {
  assert.equal(fechaEntrada({ caption: "https://oddsnamao.com/calculadora/lucroseguro?calc=x" }), true);
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

  await mensagemDoTelegram(555, { sticker: { file_unique_id: "s" } });
  await mensagemDoTelegram(555, { photo: [{ file_id: "foto-aposta-aaaaaaaa" }], caption: "https://cassino.bet.br/x" });
  assert.equal(pushes().length, 0, "sem push antes do Resumo");
  await mensagemDoTelegram(555, resumo);

  assert.deepEqual(pushes().sort(), ["fcm-ana-celular", "fcm-ana-tablet"]);
  assert.match(mensagensDoBot().at(-1).text, /Entrada recebida/);
  assert.equal(mensagensDoBot().at(-1).chat_id, "555");

  const daAna = await (await chamar("GET", "/updates?after=0", { auth: anaCelular })).json();
  assert.equal(daAna.result.length, 3);
  // O tablet também recebe tudo (buscar não apaga)
  const doTablet = await (await chamar("GET", "/updates?after=0", { auth: anaTablet })).json();
  assert.equal(doTablet.result.length, 3);
  // Cursor: depois do 2º, só o Resumo
  const depois = await (await chamar("GET", `/updates?after=${daAna.result[1].update_id}`, { auth: anaCelular })).json();
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
  await mensagemDoTelegram(555, resumo);
  await mensagemDoTelegram(555, resumo);

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
