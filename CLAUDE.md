# Tips (projeto "Apostas") — guia para o Claude

App Android pessoal de controle de banca de apostas esportivas, com foco em **surebets**.
O nome visível do app é **Tips**; o pacote é `com.example.apostas` (versão 1.1, code 2).
O dono (dede2002) fala português; **todo o código, comentários, UI e mensagens são em português** — mantenha assim.

O projeto tem duas partes:
- `app/` — o app Android (Kotlin + Jetpack Compose + Room).
- `servidor/` — servidor do "modo instantâneo" e multiusuário (Cloudflare Workers + D1 + Firebase Cloud Messaging).

Relatório da revisão de código original (com status do que foi corrigido):
`C:\Users\dedep\OneDrive\Cérebro Claude\Pessoal\Tips\Revisão de código - Apostas.md`

---

## 1. Como compilar, testar e instalar

- JDK: `C:\Program Files\Java\jdk-20` (definir `JAVA_HOME` antes do Gradle).
- Android SDK: `C:\Users\dedep\AppData\Local\Android\Sdk` (build-tools 36.0.0 tem `aapt2`, `dexdump`).
- Build + testes (PowerShell):
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-20'; .\gradlew.bat --console=plain testDebugUnitTest assembleDebug
  ```
- APK: `app\build\outputs\apk\debug\app-debug.apk` (debug, assinado com a chave de debug deste PC).
- **Pegadinha do tamanho:** o empacotamento incremental do Gradle às vezes deixa o APK de debug com ~68 MB
  (sobras internas). Antes de entregar, apague `app\build\outputs\apk\debug\app-debug.apk` e
  `app\build\intermediates\apk\debug` e rode o `assembleDebug` de novo → ~23,8 MB.
- **Entrega ao celular:** copiar o APK para `C:\Users\dedep\OneDrive\Cérebro Claude\Pessoal\Tips\`.
  **O usuário pediu (23/10) para não criar APKs novos: substituir o mais recente** (mesmo nome). Antes ele usava um
  nome novo por versão porque o app do OneDrive no celular chegou a guardar cópia antiga com nome repetido —
  se o celular instalar versão velha, conferir a data do arquivo no OneDrive. O mais recente hoje é
  **`Tips-v10-entrada-incompleta.apk`** (contém tudo das anteriores; v4–v9 podem ser apagadas). `Tips.apk` é o original de 18/10 (não usar).
  Substituir esse mesmo arquivo nas próximas entregas.
- **Sempre instalar por cima. Nunca desinstalar** (apaga o banco de apostas do celular; o usuário já fez isso uma vez).
- Celular do usuário: Samsung. Não há emulador utilizável neste PC (a AVD `Pixel_9_Pro_XL` está vazia, sem system image),
  e o celular não costuma estar conectado via ADB. Testes de dispositivo são feitos pelo usuário.
- Lint: `.\gradlew.bat lintDebug` (na revisão deu 0 erros, só avisos de dependências/recursos).

### Testes unitários (JVM) — `app/src/test/java/com/example/apostas/`
- `RecalcularLucroEditadoTest` — lucro ao editar aposta resolvida (Green/Red/Surebet/Cassino/Duplo).
- `ParseValorBRTest` — números no formato brasileiro.
- `LucroPorCasaTest` — divisão do lucro por casa (Duplo Green/Red, surebet antiga).
- `ParsearSurebetsTest` — parser das entradas do Telegram, OCR simulado do print real, casas pelo domínio, formato antigo,
  pernas (aposta/tipo/odd/stake de cada casa), JSON das pernas, entrada com texto do adm e link embutido em "Link".
- `LembreteApostasAbertasTest` — regra dos 5 dias do lembrete de apostas em aberto.
- `org.json:json` está em `testImplementation` (o org.json do Android é stub na JVM).
- Total atual: 44 testes, todos passando.

---

## 2. Estrutura do app

```
app/src/main/java/com/example/apostas/
  MainActivity.kt              tela principal (lista de apostas, filtros, card com Green/Red/Em aberto e
                               "Ver apostas de cada casa"), diálogo "Em qual casa caiu o lucro?" (DialogoCasaDoResultado,
                               com DetalhePerna e VisualizadorPrint), compartilhar, importação ao abrir o app,
                               pedido de permissão de notificação, filtro pedido por notificação (extra "filtro")
  data/                        Room: Aposta, DepositoManual, Saque, LucroTotal, LucroDiario, NotaEntity + DAOs
                               AppDataBase.kt (versão 10, migrações 7→8→9→10); Pernas.kt (o que foi apostado em cada casa)
  telegram/                    importação automática (seção 4), PrintsApostas.kt (prints salvos + limpeza),
                               LembreteApostasAbertas.kt (aviso diário de apostas em aberto)
  ui/
    CadastroApostaActivity.kt  nova aposta / edição (recalcularLucroEditado fica aqui)
    CadastroSureActivity.kt    nova surebet manual (até 5 casas)
    CadastroTigrinhoActivity.kt novo "Cassino ♠️"
    DepositoManualActivity.kt  depósito
    GraficoLucroAvancadoActivity.kt histórico/gráfico + bloco de notas
    ImportacaoTelegramActivity.kt   tela "Importar do Telegram" (conectar, status, bateria, teste de notificação)
    CadastrasDepositoActivity.kt    CÓDIGO MORTO (nem está no manifest)
    components/CampoCasadeAposta.kt campo com sugestões + lista `casasDeAposta` (nomes canônicos das casas)
    screens/EstatisticasScreen.kt   banca, lucro diário, saldos por casa, sacar (com diálogo de valor)
    screens/SurebetScreen.kt        calculadora de surebet (freebet, modo manual, duplo green)
    theme/                          tema Compose
  util/Valores.kt              parseValorBR (aceita "1.234,56", "R$ 50", "2.5")
```

Filtros da tela principal (`ui/FiltroAposta.kt`): Hoje, Mais recentes (as 30 últimas por id, ou seja, por ordem de chegada),
Em Aberto, Todas, Greens, Reds, Por Data.

Navegação: barra inferior em MainActivity (Apostas / Estatísticas / Surebet). A tela do Telegram é acessada em
Surebet → Nova Surebet → "📲 Importar do Telegram" (botão em CadastroSureActivity).

Visual: tema escuro fixo, cores repetidas nos arquivos — fundo `0xFF141824`, card `0xFF1A1F30`, campo `0xFF1E2338`,
azul de destaque `0xFF4F6FFF`, verde `0xFF81C784`/`0xFF2E7D32`, vermelho `0xFFEF9A9A`/`0xFFB71C1C`.
Ao criar diálogos/cards novos, seguir esse estilo (ex.: `DialogoCasaDoResultado` em MainActivity).

---

## 3. Modelo de dados e regras de negócio (IMPORTANTE)

### `Aposta` (tabela `apostas`)
| Campo | Significado |
|---|---|
| `descricao` | Texto livre. **O tipo da aposta é deduzido do prefixo**: `"Surebet ✅"` ou `"Cassino ♠️"` (o ♠ tem o seletor `\uFE0F`). Qualquer outro = aposta normal. |
| `casa` | Casa da aposta. Em surebet guarda várias: `"Bet365 \| Betano"` (separador `" \| "`). |
| `valor` | Aposta normal: stake. **Surebet/Cassino: o lucro esperado** (não é stake). |
| `odds` | Aposta normal: odd. Surebet/Cassino: sempre `1.0`. |
| `retornoPotencial` | `valor × odds`. |
| `lucro` | **`0.0` = Em aberto**. Green: normal = `retornoPotencial − valor`; Surebet/Cassino = `valor`. Red = `−valor`. |
| `data` | Texto `dd/MM/yyyy`. |
| `casaLucro` (v8) | Em operação com várias casas, a casa onde caiu o resultado. No Duplo Green/Red guarda as casas marcadas (`"A \| B"`). Null em aposta de uma casa, em aberto, e em surebets antigas. |
| `origemTelegram` (v9) | Chave única da operação importada (`"tg:<chatOrigem>:<msgId>"` ou `"tg:bot:<msgId>"`). Índice UNIQUE: impede importar duas vezes. Null em apostas manuais. Precisa ser preservada ao editar (CadastroApostaActivity já faz). |
| `pernasJson` (v10) | JSON de `Perna` (`data/Pernas.kt`): para cada casa, `casa`, `aposta` (ex.: "Ambas equipes Marcam: Sim"), `tipo` ("Back"/"Lay"), `odd`, `stake`, `print` (nome do arquivo em `filesDir/prints`). Só em surebets importadas a partir da v10. Preservado ao editar. |

Extensões em `data/Aposta.kt`: `casas`, `casasDoResultado`, `isResultadoDuplo`, `isDuploGreen`, `isDuploRed`, `lucroPorCasa`.
Em `data/Pernas.kt`: `pernas`, `pernaDaCasa(casa)`, `pernasParaJson`, `pernasDeJson`.

### Saldos por casa (EstatisticasScreen.carregarDados)
`saldo da casa = depósitos − saques + Σ(lucro das apostas resolvidas atribuído à casa)`.
A atribuição usa `lucroPorCasa`: aposta de uma casa → a própria `casa`; surebet com `casaLucro` → essa(s) casa(s),
**dividindo igualmente** no Duplo Green/Red; surebet antiga sem `casaLucro` → conta combinada `"A | B"` (legado;
o usuário pode tocar Green de novo para escolher a casa). Saldos negativos são zerados e escondidos.

### Comportamentos de saldo (decisões existentes do dono — não "corrigir" sem perguntar)
- Excluir uma aposta resolvida insere um `DepositoManual` de compensação igual ao lucro (o dinheiro já ganho/perdido continua na casa). No Duplo, um depósito por casa.
- Nova aposta de **uma casa só**: se o saldo da casa não cobre a stake, insere depósito automático do que falta.
- Editar valor de aposta de uma casa: insere depósito da diferença. Operações com várias casas não geram depósito automático.
- `LucroTotal` ("Banca Total") e `LucroDiario` são **contadores guardados à parte**, ajustados em Green/Red/edição
  (não recalculados das apostas). O usuário pode editar a banca e zerar o lucro diário manualmente.

### Marcar resultado (MainActivity → CardAposta)
- Botões Green / Red / Em aberto. Em operação com 2+ casas abre o `DialogoCasaDoResultado`:
  escolher uma casa, ou **Duplo Green / Duplo Red** (marcar ≥ 2 casas + digitar o valor total, dividido igualmente).
- Para o usuário saber qual casa bateu, cada casa no diálogo mostra a perna dela (aposta, Back/Lay, odd, stake) e um
  ícone 🖼️ que abre o print em tela cheia (`VisualizadorPrint`). O card também tem "Ver apostas de cada casa ▾".
- A atualização roda em `db.withTransaction` (atualiza aposta + LucroTotal + LucroDiario).
- Editar aposta resolvida mantém o status: `recalcularLucroEditado` (Red = −valor; Green normal = retorno − valor;
  Surebet/Cassino = valor; Duplo mantém o total digitado).

### Banco (Room) — regra de ouro
- Versão atual **10**. `exportSchema = true`, esquemas em `app/schemas/` (versionar no git).
- **Não existe `fallbackToDestructiveMigration`** (foi removido de propósito: apagava tudo). Qualquer mudança de entidade:
  subir a versão **e** escrever `Migration` em `AppDataBase.kt` (`addMigrations(...)`). Room usa **kapt**.
- Migrações: 7→8 `ADD COLUMN casaLucro`; 8→9 `ADD COLUMN origemTelegram` + índice único `index_apostas_origemTelegram`;
  9→10 `ADD COLUMN pernasJson`.

### Outras convenções
- Números digitados: sempre `parseValorBR(...)` (nunca `toDoubleOrNull` direto).
- Botões de salvar usam `var salvando` + `enabled = !salvando` (evita duplicar com toque duplo).
- Datas de seleção: `DatePickerDialog` aberto em `LaunchedEffect` com `setOnDismissListener` para liberar o estado.
- Não há ViewModel/Flow: telas abrem o banco e recarregam (refatoração pendente, seção 8).

---

## 4. Importação automática do Telegram (pasta `telegram/`)

### Formato das entradas do grupo de surebets (grupo "Lucro Seguro", tópico "Apostas")
1. Sticker "Fique atento! Nova entrada em breve".
2. Print da aposta principal com o **link** da aposta na legenda (às vezes a mesma aposta em 2 casas).
3. Print de cada proteção (ex.: lay na exchange) com o link na legenda.
4. Print do **"Resumo"** com o link da calculadora `https://oddsnamao.com/calculadora/lucroseguro?calc=...`.
Jogo, mercado, odds e lucro **só existem nos prints**; os links só dizem as casas.
O Resumo tem a tabela: Casa · Tipo · Odd · Stake · Responsabilidade · Retorno · **Resultado** (`+R$25,31` por linha),
e no rodapé "Valor investido R$2.203,69" e "ROI +1,15%".

### Fluxo
- O usuário **encaminha** as mensagens para o bot **@MinhasTips_bot** (conversa privada).
- O servidor recebe (webhook), guarda por conversa e, quando chega o Resumo, manda **push FCM** para os aparelhos daquela
  pessoa e responde "📥 Entrada recebida" no Telegram.
- O push (`PushTipsService`) dispara `ImportacaoAutomatica.importarPorPush` (WorkManager expedited, `APPEND_OR_REPLACE`).
- Reserva: `ImportacaoWorker` periódico a cada 15 min (rede conectada) + importação ao abrir o app (MainActivity.onResume, mostra Toast).
- `ImportadorTelegram.importar` (com `Mutex`): busca `GET /updates?after=<ultimo_update_id>` no servidor (páginas de 100),
  junta com o **buffer** de mensagens pendentes (operação incompleta), separa operações, extrai, salva com `insertSeNova`
  (IGNORE pela coluna única), grava buffer e `ultimo_update_id`. Pendências com mais de 3 dias são descartadas.
- Ordem segura: grava apostas e buffer **antes** de avançar o cursor; refazer é idempotente.

### Parser (`ParserOperacao.kt`)
- `separarOperacoes`: sticker, operação fechada ou **pausa > 4 s** entre mensagens (`recebidaUnix`) começa operação nova
  (descarta o que estava aberto); a operação **fecha** na mensagem com link da calculadora (`oddsnamao` ou `/calculadora`)
  ou em "LUCRO DA OPERAÇÃO"/"Lucro Líquido". Só entram mensagens de aposta (`isAposta`: print ou texto do adm).
- **Entrada incompleta é descartada** (`isOperacaoCompleta`, mesma regra do servidor `entradaCompleta`): com texto do adm,
  precisa de "APOSTA PRINCIPAL" e de todas as "PROTEÇÃO DA APOSTA k/N" (sem numeração: pelo menos uma); só prints:
  pelo menos 2 prints de aposta antes do fechamento.
- `casaDoLink`: domínio → casa. `<marca>.bet.br` usa a marca (ex.: `cassino.bet.br` → **CassinoBet**,
  `mexchange.betbra.bet.br` → **BetBra**); senão o rótulo antes do TLD. Casa pelo nome normalizado na lista
  `casasDeAposta`, depois `marca+"bet"`, depois prefixo; senão a marca capitalizada. Ignora t.me, oddsnamao etc.
- `partidaDoLink`: trecho do caminho com `-vs-`/`-x-` → "Georgia vs Irlanda do Norte".
- `descricaoDoPrint` (OCR do 1º print): linha "A vs B" (+ mercado na linha seguinte, pulando data/hora e seleções
  como "Sim"), ou "A VS B - MERCADO" (print da exchange). Remove o "X" de fechar no fim da linha.
- `lucroDoResumo`: **menor** valor com sinal (`+R$` pode ter espaço; `-` só vale **colado** no `R$`, porque um "-"
  solto é célula vazia da coluna Responsabilidade); depois linha "Lucro ... R$"; por fim "investido × ROI%".
  Aceita OCR lendo `R$` como `RS`/`R5`.
- Descrição salva: `"Surebet ✅ - <jogo> - <mercado>"`. Surebet importada: `valor = retornoPotencial = lucro`, `odds = 1.0`,
  `lucro = 0.0` (em aberto). Sem lucro legível → `valor = 0` e a notificação pede para preencher.
- **Pernas** (o que foi apostado em cada casa): o app lê o print de **cada** aposta. `selecaoDoPrint` tira mercado +
  seleção ("Ambas equipes Marcam: Sim"; na exchange, a linha depois de "Aposta contra/a favor"; odds de 2 casas
  decimais no fim da linha são removidas, "Over 2.5" é mantido). `linhasDaTabelaResumo` lê "Casa N · Back/Lay · odd ·
  R$ stake" do Resumo; "Casa 1" = 1º print de aposta, "Casa 2" = 2º etc.
- Data da operação = data **original** da 1ª mensagem (`forward_origin.date`), não a do encaminhamento.
- **Entrada com texto do adm** (legenda de cada print com "APOSTA PRINCIPAL"/"PROTEÇÃO DA APOSTA 1/2", "Partida:",
  "Mercado:", "Valor/Limite:", "Odd:", "Casa:"; fecha com "LUCRO DA OPERAÇÃO" + "Lucro Líquido:" + print do Resumo):
  `camposDoTexto` é a fonte principal (casa pelo nome, mercado sem "(Apostar contra LAY)" e tipo Lay, odd, stake);
  o OCR do print só roda se a legenda não tiver mercado. Lucro: "Lucro Líquido:" do texto, depois OCR do Resumo.
  "Valor Necessário em Conta" é ignorado. Casa fora da lista fica como o adm escreveu (ex.: "Sporty bet").
- Links embutidos na palavra ("Link", "Link do Cálculo") vêm só nas `entities` (`text_link`): `mensagemDoUpdate`
  acrescenta essas URLs no fim do texto.
- Formato antigo **só em texto, sem prints** (texto "APOSTA PRINCIPAL", "Casa:", "Lucro Líquido:") continua suportado.

### OCR (`OcrPrints.kt`)
- ML Kit **via Google Play Services** (`play-services-mlkit-text-recognition`), modelo baixado pelo celular
  (meta-data `com.google.mlkit.vision.DEPENDENCIES=ocr`). A versão embutida deixava o APK com 68 MB — não usar.
- `juntarPorLinha`: o ML Kit devolve cada célula de tabela separada; pedaços com centro vertical próximo
  (< 60% da altura) viram uma linha, ordenados pela esquerda.
- Só foi testado com texto simulado a partir de um print real; ajustes finos podem ser necessários com prints de outras casas.

### Notificações, bateria e preferências
- `NotificacoesImportacao`: canal "importacao_telegram" (`CANAL`), uma notificação por surebet (tocar abre a edição),
  `notificarTeste`, `podeNotificar`. Ícone `res/drawable/ic_notificacao_surebet.xml` = **o "T" do ícone do app**
  (silhueta de uma cor; o Android pinta de branco/cinza na barra). Cor de destaque `COR_TIPS` = laranja `0xFFF97316`
  (`setColor` em todas as notificações).
- `LembreteApostasAbertas`: chamado pelo `ImportacaoWorker`; no máximo **uma notificação por dia, a partir das 9h**
  (prefs `"lembretes"` → `abertas_ultimo_dia`), juntando as apostas em aberto com **5 dias ou mais** desde a data
  (todas as apostas, não só Telegram). Tocar abre o app no filtro **Em Aberto** (extra `"filtro"`). A permissão de
  notificação é pedida para todo usuário ao abrir o app.
- `PrintsApostas`: na importação, salva o print de cada casa reduzido (largura 1080, JPEG 75, ~80 KB) em
  `filesDir/prints/<hash da origem>-<i>.jpg`. `limpar` (fim de cada importação) apaga prints de apostas **resolvidas**
  cuja data passou de **15 dias** e de apostas excluídas; apostas em aberto mantêm os prints. Com 30 entradas/dia o
  espaço estabiliza em ~90 MB (estimativa; não medido com prints reais).
- `SegundoPlano`: verifica e pede "sem restrição de bateria" (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Na Samsung,
  com "Otimizar", o worker de 15 min atrasava horas.
- `SharedPreferences "telegram_prefs"` (**excluído do backup** em `res/xml/backup_rules.xml` e `data_extraction_rules.xml`):
  `aparelho_id`, `aparelho_chave`, `aparelho_conectado`, `aparelho_nome`, `bot_nome`, `ultimo_update_id`,
  `buffer_mensagens` (JSON), `ultima_importacao_*`, `segundo_plano_*`.
  `apagarConfiguracaoAntiga` remove chaves de versões antigas (`bot_token`, `chat_id`, `servidor_url`, `servidor_chave`).

### Conexão do aparelho (tela "Importar do Telegram")
"Conectar com o Telegram" → `POST /aparelhos` (ou `/aparelho/codigo`) → abre `https://t.me/MinhasTips_bot?start=<código>`
→ usuário toca Iniciar → ao voltar, o app consulta `GET /aparelho` por ~30 s → salva conectado, registra o token FCM
(`POST /aparelho/fcm`), agenda o worker e importa. "Desconectar este celular" → `DELETE /aparelho`.

---

## 5. Servidor (`servidor/`)

- Cloudflare Worker **`tips-servidor`**, URL **`https://tips-servidor.tips-servidor.workers.dev`**
  (embutida no app como `SERVIDOR_URL` em `telegram/ServidorTips.kt`).
- Banco D1 **`tips`** (id `6236bb8f-c96c-486c-a347-f99be8ba57cf`, em `wrangler.toml`). Cron diário `0 6 * * *`.
- Código: `src/index.js` (JS puro, sem dependências em runtime). Esquema: `schema.sql`; migração v1→multiusuário:
  `migracoes/0002_multiusuario.sql` (já aplicada em produção).
- Tabelas: `updates(update_id PK, json, recebido_em, chat_id)`, `aparelhos(id, chave_hash, chat_id, nome, fcm_token, criado_em, visto_em)`,
  `codigos(codigo, aparelho_id, criado_em)` (15 min), `config(chave, valor)` (cache do token OAuth do FCM e do @ do bot).
- Rotas:
  - Públicas: `POST /telegram` (webhook; valida `X-Telegram-Bot-Api-Secret-Token` = sha256(`APP_SECRET + ":webhook"`)),
    `POST /aparelhos`, `GET /diagnostico` (diz se os segredos existem/são válidos, sem expor valores).
  - Aparelho (`Authorization: Bearer <id>.<chave>`, chave guardada só como hash): `GET /aparelho`, `POST /aparelho/codigo`,
    `POST /aparelho/fcm`, `DELETE /aparelho`, `GET /updates?after=N&limit=100` (só a conversa da pessoa; **não apaga**,
    cada aparelho tem seu cursor), `GET /foto?file_id=` (só se o file_id estiver numa mensagem da pessoa; o servidor baixa do Telegram).
  - Dono (`Authorization: Bearer <APP_SECRET>`): `POST /admin/ativar` (setWebhook), `GET /admin/status`.
- Bot: só conversas privadas. `/start <código>` conecta e responde "✅ Conectado ao Tips!"; `/start` sem código explica;
  quem não conectou e encaminha o Resumo recebe instrução (nada é guardado).
- **Uma resposta por envio** (a pessoa encaminha a entrada toda de uma vez; chegam no mesmo segundo; entrada nova após
  sticker, fechamento ou pausa > 4 s pelo `message.date`):
  fechamento (`conferirEntrada`, espera 1,5 s) → completa: push + "📥 Entrada recebida"; incompleta: **sem push** e o aviso
  genérico "⚠️ Entrada incompleta. Ela foi desconsiderada… encaminhe de novo, completa e de uma vez só" (sem dizer o que faltou,
  pedido do usuário). Aposta sem fechamento (`conferirSeFicouSemFechamento`): espera 5 s; se não chegou mais nada da mesma
  entrada, manda o mesmo aviso. As esperas usam `env.DORMIR` nos testes (`fimDoEnvio()`).
  As mensagens da entrada rejeitada continuam no banco (o app as descarta pela mesma regra; somem em 7 dias).
- Push: FCM HTTP v1 com JWT RS256 assinado no Worker (WebCrypto), token OAuth em cache ~1 h; token `UNREGISTERED` é limpo.
- `limparChave()` remove espaços, quebras de linha, aspas e caracteres de controle/invisíveis de segredos e chaves.
- Segredos no Worker: `TELEGRAM_TOKEN`, `APP_SECRET` (24 caracteres), `FCM_SERVICE_ACCOUNT`. (`CHAT_ID` existe mas não é mais usado.)
- **Pegadinha do Windows:** o prompt escondido do `wrangler secret put` NÃO recebe colagem no PowerShell do usuário
  (gravou 1 caractere invisível). Sempre cadastrar com o valor no comando:
  `"VALOR" | npx wrangler@4 secret put NOME --name tips-servidor` e
  `Get-Content "arquivo.json" -Raw | npx wrangler@4 secret put FCM_SERVICE_ACCOUNT --name tips-servidor`.
  Depois conferir em `/diagnostico`. Nunca digitar/cadastrar segredos pelo usuário — dar o comando e ele roda.
- Comandos (na pasta `servidor/`): `npm test` (node:test com **SQLite real** `node:sqlite`, Node 24+, Telegram/Firebase falsos —
  17 testes), `npx wrangler deploy`, `npx wrangler d1 execute tips --remote --command "..."`.
  O wrangler está logado (OAuth) na conta Cloudflare do usuário neste PC. Teste local: `npx wrangler dev --local` com `.dev.vars` (ignorado no git).

### Contas e serviços
- Bot do Telegram: **@MinhasTips_bot** (token só no servidor).
- Firebase: projeto **`apostas-fb2bd084`**, app Android `com.example.apostas`. `app/google-services.json` fica só no PC
  (no `.gitignore`); o plugin `com.google.gms.google-services` só é aplicado se o arquivo existir. Sem ele o app compila, só sem push.
- As chaves da conta de serviço do Firebase (`apostas-fb2bd084-firebase-adminsdk-*.json`) foram baixadas em `Downloads` (duas vezes);
  orientei o usuário a apagá-las de lá e revogar a que sobrou.

### Limites (plano grátis) e escala
- Workers 100 mil req/dia; D1 5 mi linhas lidas e 100 mil gravadas por dia; FCM sem limite prático.
- Custo aproximado: ~4 req por entrada no webhook + ~4 por aparelho (updates + fotos); ~120 req/dia por aparelho parado (worker 15 min + aberturas).
- Primeiro gargalo com muitos usuários: `/foto` verifica o dono com `instr(json, ...)` nas mensagens dos últimos 7 dias da pessoa
  (muitas linhas lidas). Correção planejada: tabela `fotos(file_id PK, chat_id)` preenchida no webhook; e aumentar o worker de reserva para 1 h.

---

## 6. Git

- Remoto: `git@github.com:dede2002/Aplicativo_Aposta.git`. Branch de trabalho: **`correcoes-revisao`** (empurrada).
  `master` **ainda não recebeu nada** (nenhum PR aberto; o usuário pediu para deixar só a branch por enquanto).
- Commits na branch: `6e1312c` (correções prioritárias), `45d0a60` (bugs restantes + Duplo Green/Red), `45c410e` (importação automática + servidor multiusuário),
  `821d9b5` (apostas de cada casa, lembrete, ícone, "Mais recentes"), depois o commit do texto do adm + aviso de entrada incompleta.
- O `CLAUDE.md` passou a ser versionado (23/10) para o usuário continuar o projeto em outro computador.
- **Outro computador:** ver a seção 9 (passo a passo).
- Commit/push só quando o usuário pedir. Mensagens de commit em português, terminando com
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Não versionar: `.kotlin/`, `.idea/appInsightsSettings.xml` (mudança do Android Studio), `app/google-services.json`,
  `servidor/.dev.vars`, `servidor/node_modules`, `servidor/.wrangler`. Avisos de LF→CRLF são normais.
- Antes de commitar, procurar segredos nos arquivos (token `\d{8,10}:...`, `BEGIN PRIVATE KEY`, `AIza...`).
- `Apostas.zip` na raiz é um backup antigo do usuário (não mexer).

---

## 7. Histórico das decisões (resumo)

1. Revisão completa do código → relatório no OneDrive. Correções: migração destrutiva removida; lucro ao editar Red/Surebet;
   offset do Telegram só após salvar; token fora do backup; timeouts; erros sem token.
2. Bugs restantes: casa do resultado em surebet (pergunta no Green/Red), limite de 5000 removido, saque com valor e confirmação,
   anti-duplo-toque, `parseValorBR`, date pickers, `lifecycleScope`, card de Red, data do Telegram, casas salvas ao girar, gráfico.
3. Duplo Green e Duplo Red (marca casas + total dividido igualmente); diálogo de casa redesenhado; "Lucro potencial" em surebet aberta.
4. Importação automática (OCR + WorkManager + notificações) → modo instantâneo com servidor (webhook + push) →
   **multiusuário**: um bot e um servidor para todos, conexão em um toque, sem token/Chat ID/senha no app.
5. Escolhas do usuário: salvar surebet importada **direto** (sem revisão); começar com 15 min e depois servidor; "tudo passa pelo servidor".
6. Problema "o usuário não lembra o que apostou em cada casa" → guardar a perna de cada casa (texto para sempre) e o print
   (apagado 15 dias após resolver). Para não acumular prints de apostas esquecidas: lembrete diário de apostas em aberto
   há 5+ dias. Ícone de notificação trocado do "✓" pelo "T" do app.

---

## 8. Pendências e ideias (o usuário disse que ainda há "bastante ajustes")

- **Calculadora oddsnamao.com**: estava fora do ar (erro 522 da Cloudflare). Quando voltar, investigar se a página
  `/calculadora/lucroseguro?calc=...` expõe JSON (casas, odds, stakes, resultados) e usar como fonte principal do lucro
  (OCR vira reserva); permitiria Duplo Green/Red com valores exatos por casa. Pedir ao usuário um link recente.
- Escala do servidor: tabela `fotos` + worker de reserva em 1 h (ver seção 5) antes de distribuir para muita gente.
- Pernas: testar com prints reais de outras casas (a leitura da seleção é heurística); campo opcional "o que apostei" por casa
  nas surebets cadastradas à mão; ideia futura de perguntar o **resultado** ("Ambas marcaram?") em vez da casa.
- Espaço dos prints: se precisar economizar, reduzir para 7 dias e/ou comprimir mais (largura 720, ~40 KB).
- Sincronização das apostas entre aparelhos/usuário (hoje cada celular tem seu banco; só as entradas do Telegram chegam iguais).
- Seção 4 da revisão (refatoração): ViewModel + Flow, coluna `tipo` em vez do prefixo da descrição, coluna `status` em vez de
  `lucro == 0.0`, dinheiro em centavos, datas ISO, `@Transaction` nos fluxos restantes, componentes/cores compartilhados,
  remover código morto (`CadastrarDepositoActivity`, `getLucrosPorPeriodo`, `LucroPorDia`, `RotasNavBar.rota`),
  limpar dependências (versões fixas vs BOM, accompanist → `enableEdgeToEdge`, kapt → KSP), R8/minify no release.
- Distribuição: hoje é APK direto. Play Store exigiria trocar o pacote `com.example...` e cumprir regras de apps de apostas;
  aviso de privacidade/LGPD para outros usuários (o bot já informa a retenção de 7 dias).
- Abrir PR de `correcoes-revisao` para `master` quando o usuário quiser.

---

## 9. Continuar o projeto em outro computador (passo a passo)

As conversas e a memória do Claude Code ficam em cada PC; o que leva o contexto é este `CLAUDE.md` (está no git).
Não precisa de Docker. Os arquivos que não vão pelo git estão no OneDrive em
`Cérebro Claude\Pessoal\Tips\Para outro PC\` (copiados em 23/10/2026).

1. **Instalar os programas**
   - Git, e uma chave SSH cadastrada no GitHub (ou clonar por HTTPS com login).
   - Android Studio (traz o JDK e o Android SDK). Abrir uma vez e deixar baixar o SDK.
   - Node.js 24 ou mais novo (testes do servidor usam `node:sqlite`).
   - O app do OneDrive logado na mesma conta (para pegar os arquivos e entregar o APK ao celular).

2. **Baixar o projeto**
   ```
   git clone git@github.com:dede2002/Aplicativo_Aposta.git Apostas
   cd Apostas
   git checkout correcoes-revisao
   ```

3. **Copiar a chave de assinatura (OBRIGATÓRIO)**
   - De `OneDrive\Cérebro Claude\Pessoal\Tips\Para outro PC\debug.keystore` para `C:\Users\<usuário>\.android\debug.keystore`
     (criar a pasta `.android` se não existir; se já existir um `debug.keystore` lá, substituir).
   - Sem ela o APK sai com outra assinatura, o celular recusa instalar por cima e a única saída seria desinstalar,
     o que **apaga todas as apostas**. Impressão SHA-256 correta:
     `75:64:F1:8C:FF:42:75:4F:46:B9:BD:D5:F4:CE:CC:3F:E4:6E:EB:22:9A:0A:47:59:09:34:CA:01:4C:8F:03:78`
     (conferir com `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android`).

4. **Copiar o Firebase do app**
   - De `...\Para outro PC\google-services.json` para `Apostas\app\google-services.json`. Sem ele o app compila, mas sem push.

5. **Compilar e testar**
   - Abrir a pasta no Android Studio e esperar o Gradle sincronizar; ou, no PowerShell, apontando `JAVA_HOME` para o JDK do
     Android Studio (normalmente `C:\Program Files\Android\Android Studio\jbr`):
     `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat --console=plain testDebugUnitTest assembleDebug`
   - Servidor: `cd servidor`, `npm install`, `npm test`.

6. **Publicar o servidor (só quando mudar algo em `servidor/`)**
   - `cd servidor` e `npx wrangler login` (uma vez; entrar na conta Cloudflare do usuário). Depois `npx wrangler deploy`.
   - Os segredos (token do bot, APP_SECRET, Firebase) ficam na Cloudflare: **não** precisa cadastrar de novo.

7. **Primeira conversa com o Claude no novo PC**
   - Abrir o Claude Code na pasta `Apostas` e pedir: "Leia o CLAUDE.md e ajuste os caminhos deste PC".
   - Caminhos a ajustar neste arquivo: JDK (seção 1), Android SDK (seção 1), pasta do OneDrive (entrega do APK, seção 1).

8. **Ao terminar em um PC, commitar e dar push** (pedir ao Claude) antes de trocar para o outro, e no outro rodar
   `git pull` antes de começar. Nunca trabalhar nos dois ao mesmo tempo sem sincronizar.
