package com.example.apostas

import com.example.apostas.telegram.MensagemTelegram
import com.example.apostas.telegram.OcrPrints
import com.example.apostas.telegram.casaDoLink
import com.example.apostas.telegram.descricaoDoPrint
import com.example.apostas.telegram.extrairOperacao
import com.example.apostas.telegram.lucroDoResumo
import com.example.apostas.telegram.mensagemDoUpdate
import com.example.apostas.telegram.partidaDoLink
import com.example.apostas.telegram.separarOperacoes
import com.example.apostas.ui.components.casasDeAposta
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ParsearSurebetsTest {

    private val linkCassino = "https://cassino.bet.br/esportes/futebol/europa/liga-das-nacoes-da-uefa-b/georgia-vs-irlanda-do-norte/e-8345541230548000896"
    private val linkBetbra = "https://mexchange.betbra.bet.br/exchange/sport/soccer/event/34043227160400023/market/34043227872200023"
    private val linkCalculadora = "https://oddsnamao.com/calculadora/lucroseguro?calc=bK7i49SL"

    private fun unix(dia: Int, mes: Int, ano: Int): Long =
        Calendar.getInstance().apply { set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis / 1000

    private val data = unix(25, 9, 2026)

    // Operação no formato do print: sticker, aposta, proteção, resumo
    private fun operacao(id: Long) = listOf(
        MensagemTelegram("", isSticker = true, stickerUniqueId = "qualquer", dataUnix = data, messageId = id),
        MensagemTelegram(linkCassino, false, dataUnix = data, fotoFileId = "foto-aposta", messageId = id + 1),
        MensagemTelegram(linkBetbra, false, dataUnix = data, fotoFileId = "foto-lay", messageId = id + 2),
        MensagemTelegram(linkCalculadora, false, dataUnix = data, fotoFileId = "foto-resumo", messageId = id + 3)
    )

    // Texto que o OCR devolveria para cada print (já com os pedaços da mesma altura juntos)
    private val ocr = mapOf(
        "foto-aposta" to listOf(
            "Simples Múltiplo Sistema",
            "Georgia vs Irlanda do Norte X", "25/09 - 13:00", "Ambas equipes Marcam",
            "Sim 1.88 2.23", "1,000 Ganho: BRL 2,230.00", "Limpar Tudo", "Fazer Aposta BRL 1,000.00"
        ),
        "foto-lay" to listOf(
            "FAZER APOSTAS", "GEÓRGIA VS NORTE IRLANDA - AMBAS MARCAM", "Aposta contra", "Sim",
            "Odd Stake Responsabilidade", "2,14 1.055,87 1.203,69"
        ),
        // Layout real do Resumo: Casa · Tipo · Odd · Stake · Responsabilidade · Retorno · Resultado
        "foto-resumo" to listOf(
            "Resumo",
            "Resultado considerando a surebet, aposte para conferir ou compartilhar",
            "CASA TIPO ODD STAKE RESPONSABILIDADE RETORNO RESULTADO",
            "Casa 1 Back 2.23 R$1.000,00 - R$ 2.230,00 +R$25,31",
            "Casa 2 Lay 2.14 R$1.055,87 R$ 1.203,69 R$ 2.230,00 +R$25,32",
            "Valor investido R$2.203,69",
            "ROI +1,15%"
        )
    )

    @Test
    fun ocrJuntaPedacosDaMesmaAltura() {
        val pedacos = listOf(
            OcrPrints.Pedaco("+R$25,31", esquerda = 900, centroY = 101f, altura = 20),
            OcrPrints.Pedaco("Casa 1", esquerda = 10, centroY = 100f, altura = 20),
            OcrPrints.Pedaco("Back", esquerda = 200, centroY = 99f, altura = 18),
            OcrPrints.Pedaco("Casa 2", esquerda = 10, centroY = 140f, altura = 20)
        )
        assertEquals(listOf("Casa 1 Back +R$25,31", "Casa 2"), OcrPrints.juntarPorLinha(pedacos))
    }

    @Test
    fun operacaoCompleta_viraSurebetComCasasJogoMercadoELucro() = runBlocking {
        val separacao = separarOperacoes(operacao(100))
        assertEquals(1, separacao.completas.size)
        assertTrue(separacao.pendentes.isEmpty())

        val op = extrairOperacao(separacao.completas.single(), casasDeAposta) { ocr[it] }!!
        assertEquals(listOf("CassinoBet", "BetBra"), op.casas)
        assertEquals("Surebet ✅ - Georgia vs Irlanda do Norte - Ambas equipes Marcam", op.descricao)
        // Lucro garantido = menor resultado entre os cenários
        assertEquals(25.31, op.lucro!!, 0.0001)
        assertEquals("25/09/2026", op.data)
        assertEquals("tg:bot:103", op.origem)
    }

    @Test
    fun semResumo_ficaPendente() {
        val separacao = separarOperacoes(operacao(100).dropLast(1))
        assertTrue(separacao.completas.isEmpty())
        assertEquals(2, separacao.pendentes.size)
    }

    @Test
    fun printsIlegiveis_usaJogoDoLinkELucroNulo() = runBlocking {
        val op = extrairOperacao(separarOperacoes(operacao(1)).completas.single(), casasDeAposta) { null }!!
        assertEquals("Surebet ✅ - Georgia vs Irlanda do Norte", op.descricao)
        assertNull(op.lucro)
    }

    @Test
    fun duasOperacoesSeguidas_eRestoDescartadoPeloSticker() {
        val lixo = MensagemTelegram("bom dia", false, messageId = 1)
        val separacao = separarOperacoes(listOf(lixo) + operacao(10) + operacao(20))
        assertEquals(2, separacao.completas.size)
        assertEquals(3, separacao.completas[0].size)
    }

    @Test
    fun casasPeloDominio() {
        assertEquals("CassinoBet", casaDoLink(linkCassino, casasDeAposta))
        assertEquals("BetBra", casaDoLink(linkBetbra, casasDeAposta))
        assertEquals("Bet365", casaDoLink("https://www.bet365.bet.br/#/AC/B1", casasDeAposta))
        assertEquals("Betano", casaDoLink("https://br.betano.com/live/", casasDeAposta))
        assertEquals("Novacasa", casaDoLink("https://novacasa.bet.br/x", casasDeAposta))
        assertNull(casaDoLink(linkCalculadora, casasDeAposta))
    }

    @Test
    fun partidaPeloLink() {
        assertEquals("Georgia vs Irlanda do Norte", partidaDoLink(linkCassino))
        assertNull(partidaDoLink(linkBetbra))
    }

    @Test
    fun descricaoDoPrintDaExchange() {
        val d = descricaoDoPrint(ocr.getValue("foto-lay"))
        assertEquals("Geórgia vs Norte Irlanda", d.partida)
        assertEquals("Ambas marcam", d.mercado)
    }

    @Test
    fun lucroDoResumo_fallbacks() {
        // OCR leu "R$" como "RS"
        assertEquals(10.5, lucroDoResumo(listOf("Casa 1 +RS 10,50", "Casa 2 +RS 11,00"))!!, 0.0001)
        // Cenário negativo (não é surebet de fato): "-R$" colado conta
        assertEquals(-3.0, lucroDoResumo(listOf("Casa 1 +R$5,00", "Casa 2 -R$3,00"))!!, 0.0001)
        // Só a linha de lucro
        assertEquals(42.0, lucroDoResumo(listOf("Lucro: R$ 42,00"))!!, 0.0001)
        // Investido × ROI (quando o OCR não pega a coluna Resultado), valor na mesma linha ou na seguinte
        assertEquals(26.0, lucroDoResumo(listOf("Valor investido R$ 2.000,00", "ROI +1,30%"))!!, 0.0001)
        assertEquals(25.34, lucroDoResumo(listOf("Valor investido", "R$2.203,69", "ROI", "+1,15%"))!!, 0.0001)
        assertNull(lucroDoResumo(listOf("Resumo", "sem números")))
    }

    @Test
    fun mensagemEncaminhadaUsaDataEIdDaOrigem() {
        val update = JSONObject(
            """
            {"update_id": 5, "message": {
                "message_id": 77, "date": 1790000000, "chat": {"id": 123},
                "forward_origin": {"type": "chat", "date": 1780000000, "message_id": 999, "chat": {"id": -100555}},
                "photo": [{"file_id": "pequena"}, {"file_id": "grande"}],
                "caption": "$linkCalculadora"
            }}
            """.trimIndent()
        )
        val msg = mensagemDoUpdate(update, "123")!!
        assertEquals("grande", msg.fotoFileId)
        assertEquals(1780000000L, msg.dataUnix)
        assertEquals("-100555:999", msg.origemId)
        assertNull(mensagemDoUpdate(update, "outro-chat"))
    }

    @Test
    fun formatoAntigoEmTexto_continuaFuncionando() = runBlocking {
        val antigo = listOf(
            MensagemTelegram("APOSTA PRINCIPAL\nPartida: Time A x Time B\nCasa: Bet365", false, dataUnix = data),
            MensagemTelegram("PROTEÇÃO DA APOSTA\nCasa: Betano", false, dataUnix = data),
            MensagemTelegram("LUCRO DA OPERAÇÃO\nLucro Líquido: R$ 1.234,00", false, dataUnix = data, messageId = 9)
        )
        val bloco = separarOperacoes(antigo).completas.single()
        val op = extrairOperacao(bloco, casasDeAposta) { error("não deve ler foto") }!!
        assertEquals(listOf("Bet365", "Betano"), op.casas)
        assertEquals("Surebet ✅ - Time A x Time B", op.descricao)
        assertEquals(1234.0, op.lucro!!, 0.0001)
    }

    @Test
    fun semDataUsaHoje() = runBlocking {
        val hoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val semData = operacao(1).map { it.copy(dataUnix = 0L) }
        val op = extrairOperacao(separarOperacoes(semData).completas.single(), casasDeAposta) { ocr[it] }!!
        assertEquals(hoje, op.data)
    }
}
