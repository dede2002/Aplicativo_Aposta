# Tips — alterações de 26/11/2026

Branch: `ccr-ac1d531d-l2cqq5` (parte do `correcoes-revisao`, commit `30c9e90`).
Não há pull request aberto, a pedido do dono.

---

## 1. O que foi feito

### 1.1 Aposta normal pelo Telegram

**Pedido:** se o usuário mandar uma aposta sozinha para o bot, o app deve anotar como aposta normal.

**Comportamento novo:**

- Um **bilhete sozinho** (um print só, sem Resumo e sem texto de proteção) vira uma **aposta normal em aberto**.
- Exemplo real que guiou a implementação: tip do canal "Especiais", um print do bilhete "Simples" da Campeonbet, com a legenda `1un`, os links "Registre-se na Evobet / Campeonbet" e `Ref: 1.95`.
- O que o app lê do print:

| Campo | De onde vem | No exemplo |
|---|---|---|
| Odd | "Cotações totais" (ou ganho ÷ valor, se faltar) | 3.00 |
| Valor | "Valor total de aposta" | R$ 20,00 |
| Jogo | linha "A vs B" (a casa quebra o nome em 2 linhas) | Olympique Lyonnais vs Lillestrom SC |
| Mercado | linhas antes do jogo, em pares título/seleção | Total: Mais de 2,5 + Total de escanteios: Mais de 9,5 |
| Casa | **primeiro link** da legenda | Evobet (ver "Pontos de atenção") |
| Data | data do envio original no canal | — |

- A descrição fica `"<jogo> - <mercado>"`, **sem** o prefixo "Surebet ✅" (é isso que a torna aposta normal).
- Saldo: segue a regra da aposta de uma casa só. Se o saldo da casa não cobre o valor, entra um depósito automático do que falta.
- Notificação: "Aposta importada: …", com valor, odd e casa. Tocar abre a edição.
- Se o OCR não ler valor ou odd, a aposta é importada mesmo assim e a notificação pede para preencher.
- Foto sem casa nos links e sem cara de bilhete (ex.: uma imagem qualquer) é **ignorada**.

**O que não mudou:** print + Resumo, 2 ou mais prints sem Resumo e texto do adm sem proteção continuam sendo "entrada incompleta".

### 1.2 Trocar a casa de uma surebet em aberto

**Pedido:** com a surebet em aberto, não dava para editar o nome da casa.

**Causa:** o menu de cada casa no `EditorSurebet` só listava as contas da mesma casa (a principal e as extras).

**Solução:** nova opção **"Trocar de casa…"** no menu, que abre um diálogo com campo e sugestões (lista de casas + contas cadastradas). A opção fica escondida na perna que usa **freebet**, porque a freebet pertence a uma casa só. Ao salvar, o saldo e os depósitos automáticos são recalculados pelas pernas (código que já existia, `editarSurebetAberta`).

---

## 2. Como foi feito

### 2.1 Arquivos alterados

| Arquivo | Mudança |
|---|---|
| `app/.../telegram/ParserOperacao.kt` | `isApostaSimples`, `separarOperacoes(buffer, agora)` com o novo campo `simples`, `bilheteDoPrint` (leitura do OCR), `extrairApostaSimples`, `ApostaSimplesExtraida`, constante `CASA_NAO_IDENTIFICADA` |
| `app/.../telegram/ImportadorTelegram.kt` | Processa as apostas sozinhas depois das surebets; textos do resumo ("aposta importada") |
| `app/.../data/SaldoCasas.kt` | `salvarApostaSimples` (grava a aposta e o depósito automático numa transação) |
| `app/.../telegram/ImportacaoAutomatica.kt` | Notificação diferente para aposta normal e surebet |
| `app/.../ui/EditorSurebet.kt` | Opção "Trocar de casa…" e `DialogoTrocarCasa` |
| `servidor/src/index.js` | `apostaSimples` e a resposta "📥 Aposta recebida!" |
| `servidor/test/servidor.test.mjs` | 2 testes novos e 1 ajustado |
| `app/src/test/.../ApostaSimplesTest.kt` | 8 testes novos (arquivo novo) |
| `CLAUDE.md` | Regra nova documentada na seção 4; contagem de testes do servidor (19) |

### 2.2 Como o app decide que é uma aposta sozinha

Uma surebet sem Resumo e uma aposta sozinha são, no começo, a mesma coisa: um print solto. A diferença é o que acontece depois. A regra usada, **igual no app e no servidor**:

1. Uma operação é o conjunto de mensagens que chegam juntas (até 4 s entre uma e outra, sem sticker no meio).
2. Se o envio **termina sem mensagem de fechamento** (Resumo ou "Lucro Líquido") e há **exatamente 1 print de aposta** (sem texto de proteção), ela é uma aposta sozinha.
3. "O envio terminou" significa: chegou um sticker ou outra mensagem depois de uma pausa maior que 4 s, **ou** já se passaram 4 s desde a última mensagem.

No servidor (`conferirSeFicouSemFechamento`), depois de esperar ~5 s sem chegar mais nada: se `apostaSimples(...)` for verdadeiro, ele manda o push e responde "📥 Aposta recebida!"; senão, mantém o aviso "Entrada incompleta".

No app, o push acorda o importador, que busca as mensagens, roda `separarOperacoes(buffer, agora)` e trata o campo `simples`.

### 2.3 Como foi verificado

| Verificação | Resultado |
|---|---|
| Servidor: `node --test` (Node 22, SQLite real, Telegram e Firebase falsos) | **19 testes passando** |
| Parser Kotlin: compilado com o `kotlinc` 2.0.21 fora do Android e rodado com JUnit | **41 testes passando** (8 novos + 33 que já existiam) |
| App Android completo (Gradle) | **não compilado** — o ambiente não tem Android SDK |

No teste do parser, o Room foi substituído por anotações vazias, e o `OcrPrints` por uma cópia só da parte pura (`juntarPorLinha`). Os arquivos de interface, do importador, da notificação, de saldo e do editor **não passaram por compilador**; foram revisados à mão.

O texto de OCR usado nos testes foi **simulado a partir do print enviado pelo dono**, não é a leitura real do ML Kit.

### 2.4 Decisões tomadas

- **Casa = primeiro link da legenda.** É o mesmo critério das surebets importadas. Sem link, a casa fica "Casa não identificada" e não há depósito automático.
- **Valor = o do print** (stake do tipster). Combina com o que o app já faz nas surebets. O usuário ajusta ao editar.
- **Sem print salvo e sem pernas** na aposta normal, para não ativar regras de surebet (saldo por perna, editor de pernas).
- **Duas apostas sozinhas em menos de 4 s** contam como uma entrada só (ver limites).

---

## 3. Pontos de atenção e limites

1. **A casa pode sair errada.** No exemplo há dois links de registro ("Evobet" e "Campeonbet"). O primeiro é a Evobet, que a legenda chama de espelho da Campeonbet. Se o correto for Campeonbet, troque ao editar. Se isso for frequente, dá para mudar a regra (por exemplo, preferir a casa que não é "espelho").
2. **Encaminhe uma aposta por vez.** Duas apostas sozinhas com menos de 4 s de diferença são tratadas como uma entrada de 2 prints, que fica incompleta.
3. **Print sozinho que era o começo de uma surebet** (as outras mensagens atrasaram mais de 4 s) vira aposta normal. Antes virava "entrada incompleta".
4. **A leitura do bilhete é heurística.** Jogo e mercado podem sair diferentes com o OCR real. Valor e odd têm leitura mais direta (rótulos fixos).
5. **Só foi testado o formato do bilhete "Simples"** da casa do exemplo. Outras casas podem ter outros rótulos.
6. **O servidor precisa ser publicado** (`wrangler deploy`). Sem isso, o app novo funciona (ele importa se a mensagem estiver no buffer), mas o bot continua respondendo "Entrada incompleta" e não manda o push imediato; a importação só ocorreria no worker de 15 min.

---

## 4. O que falta para testar

### 4.1 Preparar

1. **Baixar o branch** no PC de trabalho:
   ```
   git fetch origin
   git checkout ccr-ac1d531d-l2cqq5
   git pull
   ```
2. **Publicar o servidor** (o login e os segredos já estão na Cloudflare):
   ```
   cd servidor
   npm test                 # deve mostrar 19 testes passando
   npx.cmd wrangler deploy
   ```
3. **Compilar o app e rodar os testes** (PowerShell; trocar o `JAVA_HOME` conforme o PC):
   ```
   $env:JAVA_HOME="<JDK 21>"; .\gradlew.bat --console=plain testDebugUnitTest assembleDebug
   ```
   É a primeira compilação real destas alterações. Se der erro, enviar a mensagem completa.
4. **Instalar por cima** (nunca desinstalar, senão o banco de apostas é apagado):
   ```
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   Depois, atualizar também o APK do OneDrive (`Tips-v10-entrada-incompleta.apk`, mesmo nome), como combinado.

### 4.2 Roteiro de teste no celular

| # | Teste | Resultado esperado |
|---|---|---|
| 1 | Encaminhar **um** bilhete do canal "Especiais" para o bot | Em ~5 s, o bot responde "📥 Aposta recebida!" |
| 2 | Abrir o app / ver a notificação | "Aposta importada: Olympique Lyonnais vs Lillestrom SC…" com R$ 20,00 @ 3,00 e a casa |
| 3 | Conferir a aposta na lista | Aposta normal em aberto; jogo, mercado, odd, valor e casa corretos |
| 4 | Editar a casa/valor | Salva normalmente; o depósito automático acompanha a diferença |
| 5 | Encaminhar o **mesmo** bilhete de novo | Não duplica (a origem é única) |
| 6 | Encaminhar uma **surebet completa** | Continua importando como antes (regressão) |
| 7 | Encaminhar 2 prints sem Resumo | Continua "Entrada incompleta" |
| 8 | Abrir uma surebet em aberto → menu da casa → **Trocar de casa…** | Escolher outra casa; salvar; o saldo das casas se ajusta |
| 9 | Perna com freebet | A opção "Trocar de casa…" não aparece |
| 10 | Marcar Green/Red na aposta normal importada | Lucro e banca atualizam como em qualquer aposta normal |

### 4.3 Se algo sair errado

- **Jogo ou mercado errado:** celular no cabo, encaminhar o bilhete de novo e rodar
  `adb logcat -d -v raw -s TipsOCR:D`. O log mostra o texto que o OCR leu; é com ele que se ajusta `bilheteDoPrint`. (O log só aparece no APK de debug.)
- **Bot responde "Entrada incompleta" para a aposta sozinha:** o servidor não foi publicado, ou foram enviadas 2 mensagens com menos de 4 s de diferença.
- **Nada aparece no app:** conferir em Surebet → Nova Surebet → Importar do Telegram se o aparelho está conectado e sem restrição de bateria.
- **Erro de compilação:** enviar a mensagem completa; as partes não compiladas fora do Android são `ImportadorTelegram.kt`, `ImportacaoAutomatica.kt`, `SaldoCasas.kt` e `EditorSurebet.kt`.

---

## 5. Pendências fora deste teste

- Decidir a regra da casa quando a legenda tem mais de um link (ponto 1 acima).
- O `README.md` e o branch `master` continuam desatualizados em relação ao `correcoes-revisao`.
- Pull request do branch: não abrir por enquanto (pedido do dono).
