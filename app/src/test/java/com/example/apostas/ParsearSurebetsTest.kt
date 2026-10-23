package com.example.apostas

import com.example.apostas.data.Perna
import com.example.apostas.data.pernasDeJson
import com.example.apostas.data.pernasParaJson
import com.example.apostas.telegram.selecaoDoPrint
import com.example.apostas.telegram.MensagemTelegram
import com.example.apostas.telegram.OcrPrints
import com.example.apostas.telegram.casaDoLink
import com.example.apostas.telegram.descricaoDoPrint
import com.example.apostas.telegram.extrairOperacao
import com.example.apostas.telegram.isOperacaoCompleta
import com.example.apostas.telegram.linksDe
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
    fun cadaCasaGuardaOQueFoiApostado_oddStakeETipoDoResumo() = runBlocking {
        val op = extrairOperacao(separarOperacoes(operacao(100)).completas.single(), casasDeAposta) { ocr[it] }!!
        assertEquals(2, op.pernas.size)

        val back = op.pernas[0]
        assertEquals("CassinoBet", back.casa)
        assertEquals("Ambas equipes Marcam: Sim", back.aposta)
        assertEquals("Back", back.tipo)
        assertEquals(2.23, back.odd!!, 0.0001)
        assertEquals(1000.0, back.stake!!, 0.0001)
        assertEquals("foto-aposta", back.fotoFileId)

        val lay = op.pernas[1]
        assertEquals("BetBra", lay.casa)
        assertEquals("Ambas marcam: Sim", lay.aposta)
        assertEquals("Lay", lay.tipo)
        assertEquals(2.14, lay.odd!!, 0.0001)
        assertEquals(1055.87, lay.stake!!, 0.0001)
    }

    @Test
    fun printsIlegiveis_pernasSoComACasa() = runBlocking {
        val op = extrairOperacao(separarOperacoes(operacao(1)).completas.single(), casasDeAposta) { null }!!
        assertEquals(listOf("CassinoBet", "BetBra"), op.pernas.map { it.casa })
        assertNull(op.pernas[0].aposta)
        assertNull(op.pernas[0].odd)
    }

    @Test
    fun selecaoComOverMantemOValorDaLinha() {
        val s = selecaoDoPrint(listOf("Time A vs Time B", "Total de gols", "Over 2.5 1.90", "Ganho: BRL 190.00"))
        assertEquals("Total de gols: Over 2.5", s.texto)
    }

    @Test
    fun pernasViramJsonEVoltam() {
        val pernas = listOf(
            Perna("CassinoBet", "Ambas marcam: Sim", "Back", 2.23, 1000.0, "abc-0.jpg"),
            Perna("BetBra", null, "Lay", null, null, null)
        )
        assertEquals(pernas, pernasDeJson(pernasParaJson(pernas)))
        assertEquals(emptyList<Perna>(), pernasDeJson(null))
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

    // Entrada com o texto do adm na legenda de cada print (Austrália vs Brasil, 3 casas)
    private val entradaComTexto = listOf(
        MensagemTelegram("", isSticker = true, dataUnix = data, messageId = 200),
        MensagemTelegram(
            "🏳 APOSTA PRINCIPAL 🏳\n\n🏆 Partida: Austrália vs Brasil\n" +
                    "🎯Mercado: Brasil marca gols nos dois tempos e Brasil vence a partida\n" +
                    "💰 Valor/Limite: R$ 1000,00\n🔥 Odd: 2.45\n🏦 Casa: Superbet\n\nLink",
            false, dataUnix = data, fotoFileId = "foto-superbet", messageId = 201
        ),
        MensagemTelegram(
            "🛡 PROTEÇÃO DA APOSTA 1/2🛡\n\n🏆 Partida: Austrália vs Brasil\n" +
                    "🎯Mercado: Brasil vence e Brasil NÃO marca em ambos os tempos\n" +
                    "💰 Valor/Limite: R$ 782,75\n🔥 Odd: 3.13\n🏦 Casa: Sporty bet\n\nLink\n\nCOD: 1GT4K6",
            false, dataUnix = data, fotoFileId = "foto-sporty", messageId = 202
        ),
        MensagemTelegram(
            "🛡 PROTEÇÃO DA APOSTA 2/2🛡\n\n🏆 Partida: Austrália vs Brasil\n" +
                    "🎯Mercado: Brasil vence (Apostar contra LAY)\n💰 Valor/Limite: R$ 1896,28\n" +
                    "💰 Valor Necessário em Conta: R$ 606,81\n🔥 Odd: 1.32\n🏦 Casa: Betbra\n\nLink",
            false, dataUnix = data, fotoFileId = "foto-betbra", messageId = 203
        ),
        MensagemTelegram(
            "📊 LUCRO DA OPERAÇÃO 📊\n\n💰 Lucro Líquido: R$60,44\n\n🔄 Alguma odd mudou?\n" +
                    "Atualize o cálculo pelo link abaixo:\n\n🔗 Link do Cálculo",
            false, dataUnix = data, fotoFileId = "foto-resumo-3", messageId = 204
        )
    )

    @Test
    fun entradaComTextoDoAdm_usaOTextoEGuardaOsPrints() = runBlocking {
        val separacao = separarOperacoes(entradaComTexto)
        val bloco = separacao.completas.single()
        val lidas = mutableListOf<String>()
        val op = extrairOperacao(bloco, casasDeAposta) { id ->
            lidas += id
            if (id == "foto-resumo-3") listOf("Casa 1 Back 2.45", "Casa 2 Back 3.13", "Casa 3 Lay 1.32") else null
        }!!

        assertEquals(listOf("Superbet", "Sporty bet", "BetBra"), op.casas)
        assertEquals("Surebet ✅ - Austrália vs Brasil - Brasil marca gols nos dois tempos e Brasil vence a partida", op.descricao)
        assertEquals(60.44, op.lucro!!, 0.0001)
        // Só o Resumo passa pelo OCR; os prints das apostas não precisam
        assertEquals(listOf("foto-resumo-3"), lidas)

        assertEquals(3, op.pernas.size)
        val principal = op.pernas[0]
        assertEquals("Brasil marca gols nos dois tempos e Brasil vence a partida", principal.aposta)
        assertEquals("Back", principal.tipo)
        assertEquals(2.45, principal.odd!!, 0.0001)
        assertEquals(1000.0, principal.stake!!, 0.0001)
        assertEquals("foto-superbet", principal.fotoFileId)

        val lay = op.pernas[2]
        assertEquals("BetBra", lay.casa)
        assertEquals("Brasil vence", lay.aposta)
        assertEquals("Lay", lay.tipo)
        assertEquals(1.32, lay.odd!!, 0.0001)
        assertEquals(1896.28, lay.stake!!, 0.0001)
    }

    @Test
    fun entradaComTexto_semResumoLegivel_tipoLayPeloTexto() = runBlocking {
        val op = extrairOperacao(separarOperacoes(entradaComTexto).completas.single(), casasDeAposta) { null }!!
        assertEquals(60.44, op.lucro!!, 0.0001)
        assertEquals(listOf(null, null, "Lay"), op.pernas.map { it.tipo })
        assertEquals(782.75, op.pernas[1].stake!!, 0.0001)
    }

    @Test
    fun entradaSemUmaProtecao_eDescartadaEAReenviadaVale() {
        val semProtecao1 = entradaComTexto.filterNot { "1/2" in it.texto }
        val separacao = separarOperacoes(semProtecao1 + entradaComTexto)
        // Só a reenviada (completa) vira operação
        assertEquals(1, separacao.completas.size)
        // principal, 2 proteções e o lucro (o sticker não entra)
        assertEquals(4, separacao.completas.single().size)
        assertTrue(separacao.pendentes.isEmpty())
        assertTrue(!isOperacaoCompleta(semProtecao1.drop(1).dropLast(1)))
    }

    @Test
    fun soPrintsComUmaApostaSo_eDescartada() {
        val umPrint = operacao(100).filterNot { it.fotoFileId == "foto-lay" }
        assertTrue(separarOperacoes(umPrint).completas.isEmpty())
        assertTrue(!isOperacaoCompleta(emptyList()))
    }

    @Test
    fun pausaLongaSemSticker_comecaOperacaoNova() {
        val antiga = MensagemTelegram(linkCassino, false, fotoFileId = "velha", recebidaUnix = 1_000)
        // Reenviada depois de uma pausa, sem sticker
        val nova = operacao(100).drop(1).map { it.copy(recebidaUnix = 4_600) }
        val op = separarOperacoes(listOf(antiga) + nova).completas.single()
        assertEquals(listOf("foto-aposta", "foto-lay", "foto-resumo"), op.map { it.fotoFileId })
    }

    @Test
    fun linkEmbutidoNaPalavraViraTexto() {
        val update = JSONObject(
            """{"update_id":1,"message":{"message_id":5,"date":1,"chat":{"id":42},
               "photo":[{"file_id":"f1"}],"caption":"Casa: Betbra\n\nLink",
               "caption_entities":[{"type":"text_link","offset":14,"length":4,"url":"$linkBetbra"}]}}"""
        )
        val msg = mensagemDoUpdate(update)!!
        assertTrue(msg.texto.endsWith(linkBetbra))
        assertEquals("BetBra", casaDoLink(linksDe(msg.texto).single(), casasDeAposta))
    }

    @Test
    fun semDataUsaHoje() = runBlocking {
        val hoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val semData = operacao(1).map { it.copy(dataUnix = 0L) }
        val op = extrairOperacao(separarOperacoes(semData).completas.single(), casasDeAposta) { ocr[it] }!!
        assertEquals(hoje, op.data)
    }
}
