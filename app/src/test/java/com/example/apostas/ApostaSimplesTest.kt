package com.example.apostas

import com.example.apostas.telegram.MensagemTelegram
import com.example.apostas.telegram.bilheteDoPrint
import com.example.apostas.telegram.extrairApostaSimples
import com.example.apostas.telegram.isApostaSimples
import com.example.apostas.telegram.separarOperacoes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Aposta normal: um bilhete sozinho (tip de canal de "Especiais"), sem Resumo nem proteção.
class ApostaSimplesTest {

    private val casas = listOf("Bet365", "Betano", "BetBra")
    private val linkCampeon = "https://campeonbet.bet.br/registro?ref=abc"
    private val linkEvobet = "https://evobet.bet.br/registro?ref=abc"
    private val linkCalculadora = "https://oddsnamao.com/calculadora/lucroseguro?calc=bK7i49SL"

    // Texto que o OCR devolveria para o bilhete real (print de 26/11, Olympique Lyonnais x Lillestrom)
    private val ocrBilhete = listOf(
        "Simples", "ABERTO",
        "Total 2.33 » 3.00", "Mais de 2,5",
        "Total de escanteios", "Mais de 9,5",
        "GOLDEN BOOST", "BB",
        "Olympique Lyonnais vs. 26/11 • 17:00", "Lillestrom SC",
        "Cotações totais 3.00", "Valor total de aposta R\$20.00", "Ganho total R\$60.00",
        "25/11 • 18:11 ID: 5700151574"
    )

    private fun foto(id: String, t: Long, texto: String = "1un\n$linkEvobet\n$linkCampeon\nRef: 1.95", mid: Long = t) =
        MensagemTelegram(texto, false, dataUnix = t, fotoFileId = id, messageId = mid, recebidaUnix = t, origemId = "-100:$mid")

    private val sticker = MensagemTelegram("", true, recebidaUnix = 1000, messageId = 1)

    @Test
    fun leOddValorJogoEMercadoDoBilhete() {
        val b = bilheteDoPrint(ocrBilhete)
        assertEquals(3.0, b.odd!!, 0.001)
        assertEquals(20.0, b.valor!!, 0.001)
        assertEquals("Olympique Lyonnais vs Lillestrom SC", b.partida)
        assertEquals("Total: Mais de 2,5 + Total de escanteios: Mais de 9,5", b.mercado)
    }

    @Test
    fun semCotacaoUsaGanhoSobreValor() {
        val b = bilheteDoPrint(listOf("Simples", "Valor total de aposta R\$20.00", "Ganho total R\$60.00"))
        assertEquals(3.0, b.odd!!, 0.001)
        assertEquals(20.0, b.valor!!, 0.001)
    }

    @Test
    fun extraiAposta_casaDoPrimeiroLinkEDescricaoSemPrefixoDeSurebet() = runBlocking {
        val a = extrairApostaSimples(foto("f1", 2000), casas) { ocrBilhete }!!
        assertEquals("Evobet", a.casa)
        assertEquals(20.0, a.valor!!, 0.001)
        assertEquals(3.0, a.odd!!, 0.001)
        assertTrue(a.descricao.startsWith("Olympique Lyonnais vs Lillestrom SC - "))
        assertFalse(a.descricao.startsWith("Surebet"))
        assertEquals("tg:-100:2000", a.origem)
    }

    @Test
    fun fotoQualquerSemCasaNemCaraDeBilheteNaoViraAposta() = runBlocking {
        val sem = foto("f1", 2000, texto = "bom dia")
        assertNull(extrairApostaSimples(sem, casas) { listOf("uma paisagem bonita") })
        assertNull(extrairApostaSimples(sem, casas) { null })
        // Com a casa no link, mesmo sem OCR a aposta entra (o usuário completa o valor e a odd)
        assertNotNull(extrairApostaSimples(foto("f1", 2000), casas) { null })
    }

    @Test
    fun apostaSozinhaEsperouAPausa_viraAposta() {
        val msg = foto("f1", 2000)
        // Acabou de chegar: ainda pode vir mais coisa junto
        assertEquals(1, separarOperacoes(listOf(msg), agora = 2001).pendentes.size)
        assertTrue(separarOperacoes(listOf(msg), agora = 2001).simples.isEmpty())
        // Esperou a pausa: aposta normal, nada fica pendente
        val sep = separarOperacoes(listOf(msg), agora = 2010)
        assertEquals(listOf(msg), sep.simples)
        assertTrue(sep.pendentes.isEmpty())
        assertTrue(sep.completas.isEmpty())
    }

    @Test
    fun proximaMensagemMostraQueOEnvioAcabou() {
        val msg = foto("f1", 2000)
        // Sticker de uma entrada nova depois: a aposta anterior estava sozinha
        assertEquals(listOf(msg), separarOperacoes(listOf(msg, sticker.copy(recebidaUnix = 2001))).simples)
        // Pausa longa até a próxima mensagem
        val outra = foto("f2", 2100)
        assertEquals(listOf(msg), separarOperacoes(listOf(msg, outra)).simples)
    }

    @Test
    fun surebetNormalNaoViraAposta() {
        val t = 3000L
        val entrada = listOf(
            sticker.copy(recebidaUnix = t),
            foto("a", t, linkCampeon, mid = 11),
            foto("b", t, linkEvobet, mid = 12),
            foto("r", t, linkCalculadora, mid = 13)
        )
        val sep = separarOperacoes(entrada, agora = t + 60)
        assertEquals(1, sep.completas.size)
        assertTrue(sep.simples.isEmpty())
        assertTrue(sep.pendentes.isEmpty())
    }

    @Test
    fun doisPrintsSemResumoNaoSaoApostaSimples() {
        val t = 3000L
        val entrada = listOf(foto("a", t, linkCampeon, mid = 11), foto("b", t, linkEvobet, mid = 12))
        assertFalse(isApostaSimples(entrada))
        val sep = separarOperacoes(entrada, agora = t + 60)
        assertTrue(sep.simples.isEmpty())
        // Continua pendente (à espera do resto); só é descartada quando outra entrada começar
        assertEquals(2, sep.pendentes.size)
    }

    @Test
    fun printSozinhoSeguidoDoResumoEhEntradaIncompleta_nemAposta() {
        val t = 3000L
        val entrada = listOf(foto("a", t, linkCampeon, mid = 11), foto("r", t, linkCalculadora, mid = 12))
        val sep = separarOperacoes(entrada, agora = t + 60)
        assertTrue(sep.simples.isEmpty())
        assertTrue(sep.completas.isEmpty())
    }
}
