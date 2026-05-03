# 📊 Apostas Manager

> Aplicativo Android para controle e automação de apostas esportivas, com foco em **Surebets** e **arbitragem**.

---

## 📱 Sobre o Projeto

O **TIPS** é um aplicativo nativo Android desenvolvido em **Kotlin com Jetpack Compose** que centraliza o gerenciamento de apostas esportivas. Com ele é possível registrar apostas, acompanhar o desempenho da banca, calcular surebets e até importar entradas automaticamente via **bot do Telegram**.

---

## ✨ Funcionalidades

### 🎯 Gestão de Apostas
- Cadastro de apostas normais, surebets e cassino
- Suporte a múltiplas casas de aposta por surebet (até 5)
- Filtros por status: Hoje, Em Aberto, Resolvidas, Greens, Reds e por data
- Marcação rápida de Green / Red / Em Aberto diretamente no card
- Edição e exclusão de apostas com correção automática de saldo
- Compartilhamento de apostas individuais ou em lote via WhatsApp

### 📈 Estatísticas e Banca
- Lucro diário com reset rápido
- Banca total editável com opção de ocultar saldo
- Resumo de atividade: dinheiro em apostas, total nas casas, lucro por categoria
- Saldo por casa de aposta com função de saque
- Histórico gráfico com filtros de período (1d, 1s, 1m, 6m, data personalizada)
- Bloco de notas integrado

### 🤖 Importação via Telegram
- Bot próprio para receber surebets encaminhadas do grupo
- Parser automático que identifica partida, casas e lucro
- Suporte a extrações de freebet
- Prévia das surebets antes de salvar
- Edição manual de cada entrada antes de confirmar
- Controle de offset para não importar entradas duplicadas
- Configuração de token e Chat ID diretamente no app (multi-usuário)

### 🧮 Calculadora de Surebet
- Suporte a 2–5 apostas simultâneas
- Modo Freebet SNR com cálculo de ROI específico
- Distribuição sugerida de stakes com arredondamento preciso
- Resultado real por cenário (1 green) com diferença de centavos
- Modo manual para ajuste livre dos valores
- Duplo green com todas as combinações possíveis
- Exibição de ROI da operação

### 💰 Depósitos e Saques
- Registro de depósitos manuais por casa
- Controle de saques com atualização automática do saldo

---

## 🛠️ Tecnologias Utilizadas

| Tecnologia | Uso |
|---|---|
| Kotlin | Linguagem principal |
| Jetpack Compose | UI declarativa |
| Room Database | Persistência local |
| Coroutines | Operações assíncronas |
| Telegram Bot API | Importação automática de surebets |
| Accompanist | System UI Controller |
| Material 3 | Design system |

---

### Pré-requisitos
- Android Studio Hedgehog ou superior
- SDK mínimo: API 26 (Android 8.0)
- Kotlin 1.9+

### Configuração do Bot do Telegram (opcional)

Para usar a importação automática de surebets:

1. Abra o [@BotFather](https://t.me/BotFather) no Telegram
2. Envie `/newbot` e siga as instruções
3. Copie o **token** gerado
4. Inicie uma conversa com seu bot e acesse:
   ```
   https://api.telegram.org/bot<SEU_TOKEN>/getUpdates
   ```
5. Copie o número em `"id"` dentro de `"chat"`
6. No app, acesse **Nova Surebet → Importar do Telegram** e insira o token e o Chat ID

---

## 📋 Tipos de Aposta Suportados

| Tipo | Descrição |
|---|---|
| **Normal** | Aposta simples com odd e valor |
| **Surebet ✅** | Arbitragem com múltiplas casas (2–5) |
| **Cassino ♠️** | Registro de lucro/prejuízo em cassino |

---

## 🔒 Privacidade

- Todos os dados são armazenados **localmente** no dispositivo
- Nenhuma informação é enviada para servidores externos, exceto para a **API do Telegram** quando a funcionalidade de importação está configurada
- O token do bot fica salvo apenas no `SharedPreferences` do dispositivo

---

## 👨‍💻 Autor

Desenvolvido por **André Costa**

