package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.data.Perna
import com.example.apostas.data.lucroPorMes
import com.example.apostas.data.lucroPorSemana
import com.example.apostas.data.pernasParaJson
import com.example.apostas.data.resolvidasNoPeriodo
import com.example.apostas.data.resultadoPorCasa
import com.example.apostas.data.resumoDoPeriodo
import com.example.apostas.data.valorApostado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RelatorioTest {

    private fun dia(d: Int, m: Int) = Calendar.getInstance().apply { set(2026, m - 1, d, 12, 0) }.time

    private fun normal(lucro: Double, data: String, casa: String = "Bet365", valor: Double = 100.0) = Aposta(
        descricao = "Flamengo vence", casa = casa, valor = valor, odds = 2.0, retornoPotencial = valor * 2,
        lucro = lucro, data = data
    )

    private val pernas = listOf(
        Perna("Bet365", tipo = "Back", odd = 2.5, stake = 100.0),
        Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
    )

    // Surebet nova (valor e odd por casa): bateu na Bet365, lucro 10
    private fun surebet(data: String) = Aposta(
        descricao = "Surebet ✅ - A vs B", casa = "Bet365 | Betano", valor = 10.0, odds = 1.0, retornoPotencial = 10.0,
        lucro = 10.0, data = data, casaLucro = "Bet365", pernasJson = pernasParaJson(pernas), stakesNasCasas = true
    )

    private val apostas = listOf(
        normal(100.0, "05/10/2026"),                    // green
        normal(-50.0, "12/10/2026", valor = 50.0),      // red
        surebet("20/10/2026"),
        Aposta(descricao = "Cassino ♠️", casa = "Betano", valor = 30.0, odds = 1.0, retornoPotencial = 30.0, lucro = 30.0, data = "21/09/2026"),
        normal(0.0, "20/10/2026")                       // em aberto: fora do relatório
    )

    @Test
    fun periodoPegaSoResolvidasDentroDasDatas() {
        assertEquals(3, resolvidasNoPeriodo(apostas, dia(1, 10), dia(31, 10)).size)
        assertEquals(1, resolvidasNoPeriodo(apostas, dia(12, 10), dia(12, 10)).size) // o dia todo conta
        assertEquals(4, resolvidasNoPeriodo(apostas, null, null).size)
    }

    @Test
    fun resumo_lucroPorTipoERetornoSobreOApostado() {
        val r = resumoDoPeriodo(resolvidasNoPeriodo(apostas, null, null))
        assertEquals(90.0, r.lucro, 0.001)
        assertEquals(3, r.greens)
        assertEquals(1, r.reds)
        assertEquals(10.0, r.lucroSurebet, 0.001)
        assertEquals(30.0, r.lucroCassino, 0.001)
        // Apostado conhecido: 100 + 50 (normais) + 240 (surebet: 100 + 140); cassino não entra
        assertEquals(390.0, r.apostado, 0.001)
        assertEquals(60.0 / 390.0 * 100, r.retornoPercentual!!, 0.001)
    }

    @Test
    fun valorApostado_soQuandoOAppSabe() {
        assertEquals(240.0, valorApostado(surebet("20/10/2026"))!!, 0.001)
        assertNull(valorApostado(surebet("20/10/2026").copy(pernasJson = null))) // surebet antiga: valor é o lucro
    }

    @Test
    fun porCasa_mesmaContaDosSaldos() {
        val r = resultadoPorCasa(resolvidasNoPeriodo(apostas, null, null)).toMap()
        assertEquals(100.0 - 50.0 + 150.0, r.getValue("Bet365"), 0.001) // surebet: Bet365 +150
        assertEquals(30.0 - 140.0, r.getValue("Betano"), 0.001)          // cassino +30, surebet −140
    }

    @Test
    fun surebetsAntigasSemCasaDoResultado_ficamDeFora() {
        val antiga = Aposta(
            descricao = "Surebet ✅", casa = "Bet365 Sporty Betano", valor = 25.0, odds = 1.0,
            retornoPotencial = 25.0, lucro = 25.0, data = "01/09/2026"
        )
        val r = resultadoPorCasa(listOf(normal(100.0, "05/10/2026"), antiga, antiga.copy(casa = "Superbet | 7Games")))
        assertEquals(listOf("Bet365" to 100.0), r)
    }

    @Test
    fun mesmaCasaComEspacoOuMaiuscula_viraUmaLinhaSo() {
        val r = resultadoPorCasa(
            listOf(normal(100.0, "05/10/2026"), normal(10.0, "06/10/2026", casa = "Bet365 "), normal(5.0, "07/10/2026", casa = "bet365"))
        )
        assertEquals(listOf("Bet365" to 115.0), r)
    }

    @Test
    fun porMesEPorSemana_doMaisRecente() {
        val resolvidas = resolvidasNoPeriodo(apostas, null, null)
        assertEquals(listOf("10/2026" to 60.0, "09/2026" to 30.0), lucroPorMes(resolvidas))
        // 05/10 e 12/10 de 2026 são segundas; semanas começam no domingo
        assertEquals(
            listOf("Semana de 18/10" to 10.0, "Semana de 11/10" to -50.0, "Semana de 04/10" to 100.0, "Semana de 20/09" to 30.0),
            lucroPorSemana(resolvidas)
        )
    }
}
