package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.data.Freebet
import com.example.apostas.data.Perna
import com.example.apostas.data.depositosParaStakes
import com.example.apostas.data.diasParaVencer
import com.example.apostas.data.freebetsVencendo
import com.example.apostas.data.ganho
import com.example.apostas.data.lucroGarantido
import com.example.apostas.data.lucroLiquidoPelasPernas
import com.example.apostas.data.lucroPorCasa
import com.example.apostas.data.pernasDeJson
import com.example.apostas.data.pernasParaJson
import com.example.apostas.data.perda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FreebetTest {

    // Extração: freebet de 50 na Bet365 @ 3,0 (rende 100 se bater) e proteção de 66,67 na Betano @ 1,5.
    // Missão para ganhar a freebet custou 4,20.
    private val freebet = Perna("Bet365", tipo = "Back", odd = 3.0, stake = 50.0, freebetId = 7, custoFreebet = 4.20)
    private val protecao = Perna("Betano", tipo = "Back", odd = 1.5, stake = 66.67)
    private val pernas = listOf(freebet, protecao)

    @Test
    fun pernaDeFreebet_naoSaiDoSaldoEGanhaSoOLucro() {
        assertEquals(0.0, freebet.perda(), 0.0)
        assertEquals(100.0, freebet.ganho(), 0.001) // 50 × (3 − 1): o valor da freebet não volta
    }

    @Test
    fun lucroDaExtracao_menosOQuePerdeuNaMissao() {
        assertEquals(33.33, lucroGarantido(pernas)!!, 0.01)
        assertEquals(29.13, lucroLiquidoPelasPernas(pernas)!!, 0.001)
        // Só a casa da freebet preenchida ainda não é surebet: nada de "lucro garantido"
        assertNull(lucroLiquidoPelasPernas(listOf(freebet)))
    }

    @Test
    fun casaDaFreebetNaoRecebeDepositoAutomatico() {
        val surebet = Aposta(
            descricao = "Surebet ✅ - Extração", casa = "Bet365 | Betano", valor = 29.13, odds = 1.0,
            retornoPotencial = 29.13, lucro = 0.0, data = "25/10/2026", pernasJson = pernasParaJson(pernas), stakesNasCasas = true
        )
        assertEquals(mapOf("Betano" to 66.67), depositosParaStakes(surebet, emptyList(), emptyList(), emptyList()))
    }

    private fun extracao(casaQueBateu: String) = Aposta(
        descricao = "Surebet ✅ - Extração", casa = "Bet365 | Betano", valor = 29.13, odds = 1.0,
        retornoPotencial = 29.13, lucro = 29.13, data = "25/10/2026", casaLucro = casaQueBateu,
        pernasJson = pernasParaJson(pernas), stakesNasCasas = true
    )

    @Test
    fun green_saldoDasCasasSegueODinheiroReal_semOPrejuizoDaMissao() {
        // Bateu a freebet: Bet365 recebe os 100, Betano perde a proteção
        val bet365 = extracao("Bet365").lucroPorCasa
        assertEquals(100.0, bet365.getValue("Bet365"), 0.01)
        assertEquals(-66.67, bet365.getValue("Betano"), 0.01)
        // Bateu a proteção: Betano ganha 33,33, a Bet365 não perde nada (era freebet)
        val betano = extracao("Betano").lucroPorCasa
        assertEquals(33.33, betano.getValue("Betano"), 0.01)
        assertEquals(0.0, betano.getValue("Bet365"), 0.01)
    }

    @Test
    fun freebetVaiEVoltaNoJson() {
        val lida = pernasDeJson(pernasParaJson(pernas))
        assertEquals(7, lida[0].freebetId)
        assertEquals(4.20, lida[0].custoFreebet!!, 0.0)
        assertNull(lida[1].freebetId)
    }

    @Test
    fun avisoDeFreebetQueVenceHojeOuAmanha() {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val agora = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 25, 10, 0) }.time
        fun dia(d: Int) = formato.format(Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, d) }.time)
        fun fb(id: Int, validade: String?, apostaId: Int? = null) =
            Freebet(id = id, casa = "Bet365", valor = 50.0, custo = 4.0, data = dia(20), validade = validade, apostaId = apostaId)

        val freebets = listOf(
            fb(1, dia(25)),               // vence hoje
            fb(2, dia(26)),               // vence amanhã
            fb(3, dia(28)),               // ainda falta
            fb(4, dia(24)),               // já venceu
            fb(5, null),                  // sem validade
            fb(6, dia(25), apostaId = 9)  // já usada
        )
        assertEquals(listOf(1, 2), freebetsVencendo(freebets, agora).map { it.id })
        assertEquals(-1L, fb(4, dia(24)).diasParaVencer(agora))
    }
}
