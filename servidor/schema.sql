-- Banco D1 do servidor do Tips (versão multiusuário).
-- Banco novo: npx wrangler d1 execute tips --remote --file schema.sql
-- Banco que já existia na versão de um usuário só: rodar migracoes/0002_multiusuario.sql

-- Mensagens recebidas do Telegram (JSON do update), separadas por conversa (= usuário).
-- Ficam 7 dias; cada aparelho guarda até onde já leu.
CREATE TABLE IF NOT EXISTS updates (
    update_id   INTEGER PRIMARY KEY,
    json        TEXT    NOT NULL,
    recebido_em INTEGER NOT NULL,
    chat_id     TEXT
);
CREATE INDEX IF NOT EXISTS idx_updates_chat ON updates (chat_id, update_id);

-- Celulares com o app. chat_id fica preenchido depois do "Conectar com o Telegram".
-- A chave do aparelho só é guardada como hash.
CREATE TABLE IF NOT EXISTS aparelhos (
    id         TEXT    PRIMARY KEY,
    chave_hash TEXT    NOT NULL,
    chat_id    TEXT,
    nome       TEXT,
    fcm_token  TEXT,
    criado_em  INTEGER NOT NULL,
    visto_em   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_aparelhos_chat ON aparelhos (chat_id);

-- Códigos de uso único do link t.me/<bot>?start=<código> (valem 15 minutos)
CREATE TABLE IF NOT EXISTS codigos (
    codigo      TEXT    PRIMARY KEY,
    aparelho_id TEXT    NOT NULL,
    criado_em   INTEGER NOT NULL
);

-- Valores internos (token de acesso do Firebase, nome do bot) em cache
CREATE TABLE IF NOT EXISTS config (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);
