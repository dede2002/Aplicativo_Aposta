package com.example.apostas.telegram

import com.example.apostas.util.parseValorBR
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Interpreta as mensagens que o grupo de surebets manda e o usuário encaminha para o bot.
//
// Formato atual de uma operação (uma "entrada"):
//   1. sticker "Fique atento! Nova entrada em breve"
//   2. print da aposta principal, com o link da aposta na legenda (às vezes em 2 casas)
//   3. print de cada proteção, com o link na legenda
//   4. print do "Resumo", com o link da calculadora (oddsnamao.com/calculadora/...) na legenda
// Jogo, mercado e lucro só existem nos prints (lidos por OCR); os links dizem as casas.
//
// Também aceita o formato antigo, em texto ("APOSTA PRINCIPAL", "Casa:", "Lucro Líquido:").

data class MensagemTelegram(
    val texto: String,
    val isSticker: Boolean,
    val stickerUniqueId: String = "",
    // Envio original (segundos desde 1970); em mensagem encaminhada, a data no grupo
    val dataUnix: Long = 0L,
    // file_id da maior versão da foto, se a mensagem tiver foto
    val fotoFileId: String? = null,
    // Id da mensagem na conversa com o bot
    val messageId: Long = 0L,
    // Id da mensagem original no grupo, quando o Telegram informa (mensagens encaminhadas)
    val origemId: String? = null,
    // Quando chegou no bot (segundos desde 1970); usado para descartar restos antigos
    val recebidaUnix: Long = 0L
)

data class OperacaoExtraida(
    val descricao: String,
    val casas: List<String>,
    // Null quando o lucro não pôde ser lido do print do Resumo
    val lucro: Double?,
    val data: String,
    // Chave única da operação (vai para Aposta.origemTelegram)
    val origem: String
)

data class SeparacaoOperacoes(
    val completas: List<List<MensagemTelegram>>,
    // Mensagens de uma operação que ainda não terminou (falta o Resumo)
    val pendentes: List<MensagemTelegram>
)

// ── Conversão de/para JSON (update do Telegram e buffer local) ─────────────

// chatId: se informado, ignora mensagens de outras conversas (o servidor já faz esse filtro)
fun mensagemDoUpdate(update: JSONObject, chatId: String? = null): MensagemTelegram? {
    val message = update.optJSONObject("message") ?: return null
    val msgChatId = message.optJSONObject("chat")?.optLong("id") ?: return null
    if (chatId != null && msgChatId.toString() != chatId) return null

    val recebida = message.optLong("date", 0L)
    val origem = message.optJSONObject("forward_origin")
    // Mensagem encaminhada: usa a data do envio original, não a do encaminhamento.
    val dataUnix = origem?.optLong("date", 0L)?.takeIf { it > 0L }
        ?: message.optLong("forward_date", 0L).takeIf { it > 0L }
        ?: recebida
    val origemId = origem?.let { o ->
        val msgId = o.optLong("message_id", 0L)
        val chatOrigem = o.optJSONObject("chat")?.optLong("id", 0L) ?: 0L
        if (msgId > 0L && chatOrigem != 0L) "$chatOrigem:$msgId" else null
    }
    val messageId = message.optLong("message_id", 0L)

    return when {
        message.has("sticker") -> MensagemTelegram(
            texto = "",
            isSticker = true,
            stickerUniqueId = message.getJSONObject("sticker").optString("file_unique_id", ""),
            dataUnix = dataUnix, messageId = messageId, origemId = origemId, recebidaUnix = recebida
        )
        message.has("photo") -> {
            val fotos = message.getJSONArray("photo")
            // O Telegram manda a mesma foto em vários tamanhos; a última é a maior.
            val maior = if (fotos.length() > 0) fotos.getJSONObject(fotos.length() - 1).optString("file_id") else null
            MensagemTelegram(
                texto = message.optString("caption", ""),
                isSticker = false,
                dataUnix = dataUnix, fotoFileId = maior, messageId = messageId,
                origemId = origemId, recebidaUnix = recebida
            )
        }
        message.has("text") -> MensagemTelegram(
            texto = message.optString("text", ""),
            isSticker = false,
            dataUnix = dataUnix, messageId = messageId, origemId = origemId, recebidaUnix = recebida
        )
        else -> null
    }
}

fun MensagemTelegram.paraJson(): JSONObject = JSONObject().apply {
    put("texto", texto)
    put("isSticker", isSticker)
    put("stickerUniqueId", stickerUniqueId)
    put("dataUnix", dataUnix)
    fotoFileId?.let { put("fotoFileId", it) }
    put("messageId", messageId)
    origemId?.let { put("origemId", it) }
    put("recebidaUnix", recebidaUnix)
}

fun mensagemDeJson(o: JSONObject) = MensagemTelegram(
    texto = o.optString("texto", ""),
    isSticker = o.optBoolean("isSticker", false),
    stickerUniqueId = o.optString("stickerUniqueId", ""),
    dataUnix = o.optLong("dataUnix", 0L),
    fotoFileId = o.optString("fotoFileId", "").ifBlank { null },
    messageId = o.optLong("messageId", 0L),
    origemId = o.optString("origemId", "").ifBlank { null },
    recebidaUnix = o.optLong("recebidaUnix", 0L)
)

// ── Separação em operações ──────────────────────────────────────────────

private val REGEX_LINK = Regex("""https?://[^\s<>"]+""")

fun linksDe(texto: String): List<String> =
    REGEX_LINK.findAll(texto).map { it.value.trimEnd(')', '.', ',', ';') }.toList()

fun isLinkCalculadora(url: String): Boolean {
    val u = url.lowercase()
    return "oddsnamao" in u || "/calculadora" in u
}

private fun isTextoAntigo(texto: String) =
    texto.contains("APOSTA PRINCIPAL") || texto.contains("PROTEÇÃO DA APOSTA")

private fun isLucroTextoAntigo(texto: String) =
    texto.contains("LUCRO DA OPERAÇÃO") || texto.contains("Lucro Líquido")

// A operação termina no print do Resumo (link da calculadora) ou, no formato antigo, na mensagem de lucro.
private fun fechaOperacao(msg: MensagemTelegram) =
    linksDe(msg.texto).any(::isLinkCalculadora) || isLucroTextoAntigo(msg.texto)

// Qualquer sticker começa uma operação nova; o que veio antes sem fechar é descartado.
fun separarOperacoes(mensagens: List<MensagemTelegram>): SeparacaoOperacoes {
    val completas = mutableListOf<List<MensagemTelegram>>()
    var atual = mutableListOf<MensagemTelegram>()
    for (msg in mensagens) {
        if (msg.isSticker) {
            atual = mutableListOf()
            continue
        }
        if (msg.texto.isBlank() && msg.fotoFileId == null) continue
        atual.add(msg)
        if (fechaOperacao(msg)) {
            completas.add(atual)
            atual = mutableListOf()
        }
    }
    return SeparacaoOperacoes(completas, atual)
}

// ── Casas e jogo a partir dos links ─────────────────────────────────────

private fun normalizar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

private val SUFIXOS_DOMINIO = setOf("com", "br", "net", "org", "io", "co", "bet", "app", "uk", "pt", "es", "eu")
private val HOSTS_IGNORADOS = listOf("t.me", "telegram", "oddsnamao", "bit.ly", "youtube", "instagram")

// "https://mexchange.betbra.bet.br/..." → "BetBra" (nome da lista de casas do app, se existir)
fun casaDoLink(url: String, casasConhecidas: List<String>): String? {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
    if (HOSTS_IGNORADOS.any { it in host }) return null
    val partes = host.split('.')

    val marca = if (host.endsWith(".bet.br") && partes.size >= 3) {
        // Domínios regulamentados no Brasil: <marca>.bet.br
        partes[partes.size - 3]
    } else {
        partes.dropLastWhile { it in SUFIXOS_DOMINIO }.lastOrNull() ?: return null
    }
    val m = normalizar(marca)
    if (m.isBlank()) return null

    return casasConhecidas.firstOrNull { normalizar(it) == m }
        ?: casasConhecidas.firstOrNull { normalizar(it) == m + "bet" }
        ?: casasConhecidas.firstOrNull { m.length >= 4 && normalizar(it).startsWith(m) }
        ?: marca.replaceFirstChar { it.uppercase() }
}

private val CONECTIVOS = setOf("do", "da", "de", "dos", "das", "e", "del", "di", "du", "y", "of", "the")

private fun formatarTitulo(texto: String): String =
    texto
        // Botão de fechar (×) que o OCR junta no fim da linha do jogo
        .replace(Regex("""\s+[xX×✕]$"""), "")
        .split(Regex("\\s+")).filter { it.isNotBlank() }.mapIndexed { i, palavra ->
        val p = palavra.lowercase(Locale("pt", "BR"))
        when {
            p == "vs" || p == "x" -> p
            i > 0 && p in CONECTIVOS -> p
            else -> p.replaceFirstChar { it.titlecase(Locale("pt", "BR")) }
        }
    }.joinToString(" ")

// ".../georgia-vs-irlanda-do-norte/e-834..." → "Georgia vs Irlanda do Norte"
fun partidaDoLink(url: String): String? {
    val caminho = runCatching { URI(url).path }.getOrNull() ?: return null
    val trecho = caminho.split('/').firstOrNull { "-vs-" in it || "-x-" in it } ?: return null
    return formatarTitulo(trecho.replace('-', ' '))
}

// ── Leitura dos prints (texto vindo do OCR) ─────────────────────────────

data class DescricaoPrint(val partida: String?, val mercado: String?)

private val REGEX_PARTIDA = Regex("""^(.{2,}?)\s+(?:vs\.?|v\.?|x|×)\s+(.{2,})$""", RegexOption.IGNORE_CASE)
private val REGEX_DATA_HORA = Regex("""\d{1,2}/\d{1,2}|\d{1,2}:\d{2}""")
private val SELECOES = setOf("sim", "não", "nao", "yes", "no", "over", "under", "mais", "menos")

private fun pareceTexto(linha: String): Boolean {
    val letras = linha.count { it.isLetter() }
    return letras >= 3 && letras >= linha.count { it.isDigit() } && "R$" !in linha
}

// Procura "Time A vs Time B" e o mercado (linha seguinte ou depois de " - ").
fun descricaoDoPrint(linhas: List<String>): DescricaoPrint {
    val limpas = linhas.map { it.trim() }.filter { it.isNotBlank() }
    for ((i, linha) in limpas.withIndex()) {
        if (!pareceTexto(linha)) continue
        if (" - " in linha) {
            val antes = linha.substringBefore(" - ").trim()
            val depois = linha.substringAfter(" - ").trim()
            if (REGEX_PARTIDA.matches(antes)) {
                return DescricaoPrint(formatarTitulo(antes), depois.takeIf { pareceTexto(it) }?.let(::formatarFrase))
            }
        }
        if (REGEX_PARTIDA.matches(linha)) {
            val mercado = limpas.drop(i + 1).firstOrNull { candidata ->
                pareceTexto(candidata) &&
                        !REGEX_DATA_HORA.containsMatchIn(candidata) &&
                        candidata.lowercase() !in SELECOES &&
                        candidata.length >= 4
            }
            return DescricaoPrint(formatarTitulo(linha), mercado?.let(::formatarFrase))
        }
    }
    return DescricaoPrint(null, null)
}

// "AMBAS MARCAM" → "Ambas marcam"; mantém como veio se já tiver minúsculas
private fun formatarFrase(texto: String): String {
    val semSimbolos = texto.replace(Regex("[^\\p{L}\\p{N}\\s.,()+\\-/]"), "").trim()
    return if (semSimbolos == semSimbolos.uppercase()) {
        semSimbolos.lowercase(Locale("pt", "BR")).replaceFirstChar { it.titlecase(Locale("pt", "BR")) }
    } else semSimbolos
}

// R$ lido pelo OCR pode virar "RS", "R5" etc.
private val RS = """R\s?[${'$'}S5]\s*"""
// "+" pode vir separado do R$; "-" só vale colado ("-R$"), porque um "-" solto é célula vazia
// da tabela (ex.: Responsabilidade da aposta Back) seguida do valor da coluna ao lado.
private val REGEX_VALOR_COM_SINAL = Regex("""(\+)\s*$RS([\d.]+,\d{2})|([\-−–])$RS([\d.]+,\d{2})""")
private val REGEX_VALOR_SINAL_DEPOIS = Regex("""$RS([+\-−–])\s*([\d.]+,\d{2})""")
private val REGEX_VALOR = Regex("""$RS([\d.]+,\d{2})""")
private val REGEX_PORCENTAGEM = Regex("""([+\-]?\d+[.,]\d+)\s*%""")

// Lucro garantido da operação a partir do print do "Resumo".
// O Resumo mostra o resultado de cada cenário com sinal (+R$ 23,31); o lucro garantido é o menor.
fun lucroDoResumo(linhas: List<String>): Double? {
    val texto = linhas.joinToString("\n")

    fun sinal(s: String) = if (s == "+") 1.0 else -1.0
    val comSinal = REGEX_VALOR_COM_SINAL.findAll(texto).mapNotNull { m ->
        val (s, v) = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2]
        else m.groupValues[3] to m.groupValues[4]
        parseValorBR(v)?.let { sinal(s) * it }
    } +REGEX_VALOR_SINAL_DEPOIS.findAll(texto).mapNotNull { m ->
        parseValorBR(m.groupValues[2])?.let { sinal(m.groupValues[1]) * it }
    }
    comSinal.minOrNull()?.let { return it }

    // Linha "Lucro ... R$ X"
    linhas.firstOrNull { it.contains("lucro", ignoreCase = true) && REGEX_VALOR.containsMatchIn(it) }
        ?.let { linha -> REGEX_VALOR.find(linha)?.groupValues?.get(1)?.let(::parseValorBR) }
        ?.let { return it }

    // Último recurso: valor investido × ROI (o valor pode estar na mesma linha ou na seguinte)
    val iInvestido = linhas.indexOfFirst { it.contains("investido", ignoreCase = true) }
    val investido = if (iInvestido < 0) null else
        linhas.drop(iInvestido).take(2).firstNotNullOfOrNull { REGEX_VALOR.find(it)?.groupValues?.get(1) }
            ?.let(::parseValorBR)
    val porcentagem = REGEX_PORCENTAGEM.find(texto)?.groupValues?.get(1)?.let(::parseValorBR)
    if (investido != null && porcentagem != null) {
        return Math.round(investido * porcentagem) / 100.0
    }
    return null
}

// ── Montagem da operação ────────────────────────────────────────────────

private fun formatarData(unix: Long): String {
    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formato.format(if (unix > 0L) Date(unix * 1000) else Date())
}

// lerFoto: file_id → linhas de texto do print (OCR), ou null se não deu para ler.
suspend fun extrairOperacao(
    bloco: List<MensagemTelegram>,
    casasConhecidas: List<String>,
    lerFoto: suspend (String) -> List<String>?
): OperacaoExtraida? {
    if (bloco.isEmpty()) return null
    val fechamento = bloco.last()
    val origem = "tg:" + (fechamento.origemId ?: "bot:${fechamento.messageId}")
    val data = formatarData(bloco.first().dataUnix)

    if (bloco.any { isTextoAntigo(it.texto) }) return extrairOperacaoTexto(bloco, casasConhecidas, data, origem)

    val mensagensAposta = bloco.filter { msg -> linksDe(msg.texto).none(::isLinkCalculadora) }
    val linksAposta = mensagensAposta.flatMap { linksDe(it.texto) }
    val casas = linksAposta.mapNotNull { casaDoLink(it, casasConhecidas) }.distinct()
    if (casas.isEmpty()) return null

    // Jogo e mercado: primeiro print que tiver "A vs B"; senão, o jogo vem do link
    var partida: String? = null
    var mercado: String? = null
    for (msg in mensagensAposta) {
        val id = msg.fotoFileId ?: continue
        val d = descricaoDoPrint(lerFoto(id) ?: continue)
        if (partida == null) partida = d.partida
        if (mercado == null) mercado = d.mercado
        if (partida != null && mercado != null) break
    }
    if (partida == null) partida = linksAposta.firstNotNullOfOrNull(::partidaDoLink)

    val lucro = fechamento.fotoFileId?.let { lerFoto(it) }?.let(::lucroDoResumo)

    val descricao = buildString {
        append("Surebet ✅")
        partida?.let { append(" - ").append(it) }
        mercado?.let { append(" - ").append(it) }
    }
    return OperacaoExtraida(descricao, casas, lucro, data, origem)
}

// Formato antigo, todo em texto.
private fun extrairOperacaoTexto(
    bloco: List<MensagemTelegram>,
    casasConhecidas: List<String>,
    data: String,
    origem: String
): OperacaoExtraida? {
    val casas = mutableListOf<String>()
    var partida = ""
    var lucro: Double? = null
    var isFreebet = false

    for (mensagem in bloco.map { it.texto }) {
        val linhas = mensagem.lines().map { it.trim() }
        when {
            isTextoAntigo(mensagem) -> for (l in linhas) {
                when {
                    l.contains("Partida:") && partida.isBlank() -> partida = l.substringAfter("Partida:").trim()
                    l.contains("Casa:") -> {
                        val casa = l.substringAfter("Casa:").trim()
                        val nome = casasConhecidas.firstOrNull { normalizar(it) == normalizar(casa) } ?: casa
                        if (nome.isNotBlank() && nome !in casas) casas.add(nome)
                    }
                }
            }
            isLucroTextoAntigo(mensagem) -> {
                if (mensagem.contains("FREEBET", ignoreCase = true)) isFreebet = true
                linhas.firstOrNull { it.contains("Lucro Líquido:") }?.let { l ->
                    lucro = parseValorBR(
                        l.substringAfter("Lucro Líquido:").split("ou").first().split("(").first()
                    )
                }
            }
        }
    }
    if (casas.isEmpty()) return null
    val descricao = when {
        isFreebet && partida.isNotBlank() -> "Surebet ✅ - Extração Jogo - $partida"
        partida.isNotBlank() -> "Surebet ✅ - $partida"
        else -> "Surebet ✅"
    }
    return OperacaoExtraida(descricao, casas, lucro, data, origem)
}
