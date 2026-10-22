# Servidor do Tips (multiusuário)

Um bot só (o do dono do servidor) atende todos os usuários. Cada pessoa conecta o app em um
toque ("Conectar com o Telegram"), encaminha as entradas para o bot e a surebet aparece no
celular dela na hora. Ninguém além do servidor conhece o token do bot.

Tudo roda no plano grátis da Cloudflare (Workers + D1) e do Firebase (push).

## Como funciona

1. **Conectar:** o app chama `POST /aparelhos` e recebe uma chave própria + o link
   `t.me/<bot>?start=<código>`. A pessoa toca em Iniciar; o bot recebe `/start <código>`,
   liga a conversa dela ao aparelho e responde "Conectado".
2. **Encaminhar:** o servidor guarda as mensagens da conversa (webhook). No print do Resumo,
   manda push para os aparelhos daquela pessoa e responde "Entrada recebida" no Telegram.
3. **Importar:** o app busca `GET /updates` (só as mensagens da pessoa) e `GET /foto` (só os
   prints dela), lê com OCR no celular e salva a surebet.

As mensagens ficam guardadas por até 7 dias (limpeza diária). As apostas ficam só nos celulares.

## Segredos

| Nome | O que é |
|---|---|
| `TELEGRAM_TOKEN` | token do bot (@BotFather) |
| `APP_SECRET` | senha do dono (rotas `/admin/*`) e base do segredo do webhook |
| `FCM_SERVICE_ACCOUNT` | JSON da conta de serviço do Firebase |

Cadastrar com o valor no próprio comando (o prompt escondido do wrangler não recebe colagem
em alguns terminais do Windows):

```
"VALOR" | npx wrangler@4 secret put NOME --name tips-servidor
Get-Content "C:\caminho\chave-firebase.json" -Raw | npx wrangler@4 secret put FCM_SERVICE_ACCOUNT --name tips-servidor
```

Conferir sem ver os valores: `https://tips-servidor.tips-servidor.workers.dev/diagnostico`

## Instalar do zero

```
npx wrangler login
npx wrangler d1 create tips          # colar o database_id em wrangler.toml
npx wrangler d1 execute tips --remote --file schema.sql
npx wrangler deploy
# cadastrar os 3 segredos (acima)
curl.exe -X POST -H "Authorization: Bearer <APP_SECRET>" https://tips-servidor.tips-servidor.workers.dev/admin/ativar
```

O app já vem com o endereço do servidor (`SERVIDOR_URL` em `ServidorTips.kt`) e precisa do
`google-services.json` do Firebase em `app/` para o push.

## Dono do servidor

- Status (usuários, aparelhos, webhook):
  `curl.exe -H "Authorization: Bearer <APP_SECRET>" https://tips-servidor.tips-servidor.workers.dev/admin/status`
- Religar o webhook: `POST /admin/ativar` (mesmo cabeçalho).

## Banco que veio da versão de um usuário só

Rodar uma vez: `npx wrangler d1 execute tips --remote --file migracoes/0002_multiusuario.sql`

## Testes

```
npm install
npm test
```

Os testes usam SQLite de verdade (`node:sqlite`, Node 24+) e um Telegram/Firebase falsos.
