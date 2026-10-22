-- Migração do banco de um usuário só (versão 1) para multiusuário.
-- Rodar UMA vez: npx wrangler d1 execute tips --remote --file migracoes/0002_multiusuario.sql

-- Mensagens passam a ter dono (a conversa com o bot)
ALTER TABLE updates ADD COLUMN chat_id TEXT;
UPDATE updates SET chat_id = CAST(json_extract(json, '$.message.chat.id') AS TEXT);
CREATE INDEX IF NOT EXISTS idx_updates_chat ON updates (chat_id, update_id);

-- Aparelhos com chave própria, no lugar da lista de tokens de push
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

CREATE TABLE IF NOT EXISTS codigos (
    codigo      TEXT    PRIMARY KEY,
    aparelho_id TEXT    NOT NULL,
    criado_em   INTEGER NOT NULL
);

DROP TABLE IF EXISTS dispositivos;
