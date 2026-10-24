package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.data.ContaCasa
import com.example.apostas.data.Perna
import com.example.apostas.data.casaDaConta
import com.example.apostas.data.contasDaCasa
import com.example.apostas.data.lucroAposEdicao
import com.example.apostas.data.lucroGarantido
import com.example.apostas.data.lucroPorCasa
import com.example.apostas.data.pernasDeJson
import com.example.apostas.data.pernasParaJson
import com.example.apostas.ui.usaEditorDeSurebet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContasEEdicaoSurebetTest {

    private val contas = listOf(
        ContaCasa(casa = "Bet365", apelido = "Irmã"),
        ContaCasa(casa = "Bet365", apelido = "Namorada"),
        ContaCasa(casa = "Betano", apelido = "Irmã")
    )

    @Test
    fun contasDeCadaCasa_principalPrimeiro() {
        assertEquals(listOf("Bet365", "Bet365 (Irmã)", "Bet365 (Namorada)"), contasDaCasa("Bet365", contas))
        // A partir de uma conta extra, mostra as da mesma casa
        assertEquals(listOf("Bet365", "Bet365 (Irmã)", "Bet365 (Namorada)"), contasDaCasa("Bet365 (Irmã)", contas))
        assertEquals(listOf("Superbet"), contasDaCasa("Superbet", contas))
        assertEquals("Bet365", casaDaConta("Bet365 (Namorada)"))
        assertEquals("Lance de Sorte", casaDaConta("Lance de Sorte"))
    }

    // Bet365 100 @ 2,5 e Betano 140 @ 1,85: bate Bet365 → 150 − 140 = 10; bate Betano → 119 − 100 = 19
    private val duasCasas = listOf(
        Perna("Bet365", tipo = "Back", odd = 2.5, stake = 100.0),
        Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
    )

    @Test
    fun lucroGarantido_piorCenario() {
        assertEquals(10.0, lucroGarantido(duasCasas)!!, 0.001)
        assertNull(lucroGarantido(duasCasas.map { it.copy(odd = null) }))
    }

    @Test
    fun dividirAMesmaApostaEmDuasContas_naoMudaOLucro() {
        val dividida = listOf(
            Perna("Bet365", tipo = "Back", odd = 2.5, stake = 50.0, grupo = 1),
            Perna("Bet365 (Irmã)", tipo = "Back", odd = 2.5, stake = 50.0, grupo = 1),
            Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
        )
        assertEquals(10.0, lucroGarantido(dividida)!!, 0.001)
    }

    @Test
    fun layNaExchange_entraComAResponsabilidade() {
        // Exemplo real (Austrália vs Brasil): lucro do Resumo R$ 60,44
        val pernas = listOf(
            Perna("Superbet", tipo = "Back", odd = 2.45, stake = 1000.0),
            Perna("Sporty bet", tipo = "Back", odd = 3.13, stake = 782.75),
            Perna("BetBra", tipo = "Lay", odd = 1.32, stake = 1896.28)
        )
        assertEquals(60.44, lucroGarantido(pernas)!!, 0.01)
    }

    @Test
    fun lucroAposEdicao_aplicaSoADiferencaSobreOLucroDoResumo() {
        // Resumo disse 9,50 (comissão); a odd da Betano caiu para 1,80 → cenário Betano 112 − 100 = 12, pior ainda 10
        val novas = duasCasas.map { if (it.casa == "Betano") it.copy(odd = 1.80) else it }
        assertEquals(9.5, lucroAposEdicao(9.5, duasCasas, novas), 0.001)
        // Odd da Bet365 subiu para 2,6 → cenário Bet365 160 − 140 = 20; pior agora 19 → +9 sobre o Resumo
        val melhor = duasCasas.map { if (it.casa == "Bet365") it.copy(odd = 2.6) else it }
        assertEquals(18.5, lucroAposEdicao(9.5, duasCasas, melhor), 0.001)
        // Antigas sem stake/odd: usa o cálculo das novas; novas incompletas: mantém
        assertEquals(10.0, lucroAposEdicao(9.5, duasCasas.map { it.copy(stake = null) }, duasCasas), 0.001)
        assertEquals(9.5, lucroAposEdicao(9.5, duasCasas, duasCasas.map { it.copy(stake = null) }), 0.001)
    }

    @Test
    fun green_marcandoUmaConta_oMesmoPalpitePagaNasDuas() {
        val pernas = listOf(
            Perna("Bet365", tipo = "Back", odd = 2.5, stake = 60.0, grupo = 1),
            Perna("Bet365 (Irmã)", tipo = "Back", odd = 2.5, stake = 40.0, grupo = 1),
            Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
        )
        val surebet = Aposta(
            descricao = "Surebet ✅ - Flamengo vs Vasco", casa = "Bet365 | Bet365 (Irmã) | Betano",
            valor = 10.0, odds = 1.0, retornoPotencial = 10.0, lucro = 10.0, data = "24/10/2026",
            casaLucro = "Bet365", pernasJson = pernasParaJson(pernas), stakesNasCasas = true
        )
        val movimento = surebet.lucroPorCasa
        assertEquals(90.0, movimento.getValue("Bet365"), 0.001)        // 60 × 1,5
        assertEquals(60.0, movimento.getValue("Bet365 (Irmã)"), 0.001) // 40 × 1,5
        assertEquals(-140.0, movimento.getValue("Betano"), 0.001)
    }

    @Test
    fun grupoVaiEVoltaNoJson() {
        val pernas = listOf(Perna("Bet365", odd = 2.0, stake = 10.0, grupo = 3), Perna("Betano"))
        val lidas = pernasDeJson(pernasParaJson(pernas))
        assertEquals(3, lidas[0].grupo)
        assertNull(lidas[1].grupo)
    }

    @Test
    fun editorSoEmSurebetEmAbertoComPernas() {
        val base = Aposta(
            descricao = "Surebet ✅ - A vs B", casa = "Bet365 | Betano", valor = 10.0, odds = 1.0,
            retornoPotencial = 10.0, lucro = 0.0, data = "24/10/2026", pernasJson = pernasParaJson(duasCasas)
        )
        assertTrue(usaEditorDeSurebet(base))
        assertFalse(usaEditorDeSurebet(base.copy(lucro = 10.0)))        // resolvida
        assertFalse(usaEditorDeSurebet(base.copy(pernasJson = null)))   // antiga
        assertFalse(usaEditorDeSurebet(base.copy(descricao = "Flamengo vence")))
    }
}
