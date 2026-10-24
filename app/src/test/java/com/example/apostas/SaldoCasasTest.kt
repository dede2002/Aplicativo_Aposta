package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.data.DepositoManual
import com.example.apostas.data.Perna
import com.example.apostas.data.Saque
import com.example.apostas.data.depositosParaStakes
import com.example.apostas.data.lucroPorCasa
import com.example.apostas.data.pernasParaJson
import com.example.apostas.data.saldoParaApostar
import com.example.apostas.data.saldosDasCasas
import com.example.apostas.data.ajusteDeSaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaldoCasasTest {

    private val pernas = listOf(
        Perna("Bet365", tipo = "Back", odd = 2.5, stake = 100.0),
        Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
    )

    private fun surebet(lucro: Double = 0.0, casaLucro: String? = null) = Aposta(
        descricao = "Surebet ✅ - Flamengo vs Vasco", casa = "Bet365 | Betano",
        valor = 10.0, odds = 1.0, retornoPotencial = 10.0, lucro = lucro, data = "23/10/2026",
        casaLucro = casaLucro, pernasJson = pernasParaJson(pernas), stakesNasCasas = true
    )

    // Saldo como a tela de Estatísticas calcula: depósitos − saques + lucros resolvidos
    private fun saldoNaTela(casa: String, apostas: List<Aposta>, depositos: List<DepositoManual>) =
        depositos.filter { it.casa == casa }.sumOf { it.valor } +
                apostas.filter { it.lucro != 0.0 }.sumOf { it.lucroPorCasa[casa] ?: 0.0 }

    @Test
    fun casaComZero_terminaComORetornoInteiroQuandoBate() {
        // Ao salvar: as duas casas estão com 0 → depósito automático da stake de cada uma
        val depositos = depositosParaStakes(surebet(), emptyList(), emptyList(), emptyList())
            .map { (casa, valor) -> DepositoManual(casa = casa, valor = valor) }
        assertEquals(mapOf("Bet365" to 100.0, "Betano" to 140.0), depositos.associate { it.casa to it.valor })

        // Bateu na Bet365 (lucro 10): Bet365 fica com os 250, Betano com 0
        val resolvida = listOf(surebet(lucro = 10.0, casaLucro = "Bet365"))
        assertEquals(250.0, saldoNaTela("Bet365", resolvida, depositos), 0.001)
        assertEquals(0.0, saldoNaTela("Betano", resolvida, depositos), 0.001)
    }

    @Test
    fun casaComSaldo_soDepositaOQueFalta() {
        val depositos = listOf(DepositoManual(casa = "Bet365", valor = 500.0), DepositoManual(casa = "Betano", valor = 50.0))
        val saques = listOf(Saque(casa = "Betano", valor = 20.0))
        val faltando = depositosParaStakes(surebet(), emptyList(), depositos, saques)
        // Bet365 tem 500 (cobre os 100); Betano tem 30 e precisa de 140
        assertEquals(mapOf("Betano" to 110.0), faltando)
    }

    @Test
    fun stakePresaEmSurebetAberta_naoContaComoSaldoLivre() {
        val depositos = listOf(DepositoManual(casa = "Bet365", valor = 100.0))
        val aberta = surebet()
        assertEquals(0.0, saldoParaApostar("Bet365", listOf(aberta), depositos, emptyList()), 0.001)
        // Lay: o que fica preso é a responsabilidade
        val comLay = aberta.copy(pernasJson = pernasParaJson(listOf(Perna("BetBra", tipo = "Lay", odd = 3.0, stake = 50.0))))
        assertEquals(-100.0, saldoParaApostar("BetBra", listOf(comLay), emptyList(), emptyList()), 0.001)
    }

    @Test
    fun depositoAutomatico_emCentavos() {
        // Saldo com sobra de conta de Double (0,1 + 0,2 − 0,3 ≈ 5,5e-17) não pode virar 794,8700000000065
        val depositos = listOf(0.1, 0.2, -0.3).map { DepositoManual(casa = "Bet365", valor = it) }
        val sure = surebet().copy(pernasJson = pernasParaJson(listOf(Perna("Bet365", tipo = "Back", odd = 3.9, stake = 794.87))))
        assertEquals(mapOf("Bet365" to 794.87), depositosParaStakes(sure, emptyList(), depositos, emptyList()))
    }

    @Test
    fun surebetAntigaOuSemStakes_naoGeraDeposito() {
        assertTrue(depositosParaStakes(surebet().copy(stakesNasCasas = false), emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun saldoQueSobraDeArredondamento_ficaZeroENaoApareceNaTela() {
        // 0,1 + 0,2 − 0,3 em Double dá 0,0000000000000000555: antes a casa aparecia com "R$ 0,00"
        val depositos = listOf(
            DepositoManual(casa = "BetBra", valor = 0.1),
            DepositoManual(casa = "BetBra", valor = 0.2),
            DepositoManual(casa = "BetBra", valor = -0.3)
        )
        val saldos = saldosDasCasas(emptyList(), depositos, emptyList())
        assertEquals(0.0, saldos.getValue("BetBra"), 0.0)
        assertTrue(saldos.filterValues { it > 0.0 }.isEmpty())
    }

    @Test
    fun saldosDasCasas_depositosSaquesELucros() {
        val apostas = listOf(surebet(lucro = 10.0, casaLucro = "Bet365"))
        val depositos = listOf(DepositoManual(casa = "Bet365", valor = 100.0), DepositoManual(casa = "Betano", valor = 140.0))
        val saques = listOf(Saque(casa = "Bet365", valor = 50.0))
        val saldos = saldosDasCasas(apostas, depositos, saques)
        assertEquals(200.0, saldos.getValue("Bet365"), 0.001)
        assertEquals(0.0, saldos.getValue("Betano"), 0.001)
    }

    @Test
    fun ajusteDeSaldo_lancaADiferenca() {
        assertEquals(30.0, ajusteDeSaldo(saldoNoApp = 120.0, saldoReal = 150.0), 0.0)
        assertEquals(-20.5, ajusteDeSaldo(saldoNoApp = 120.5, saldoReal = 100.0), 0.0)
        // Saldo negativo no app (a tela mostra 0): casa vazia de verdade → ajuste traz para 0
        assertEquals(35.0, ajusteDeSaldo(saldoNoApp = -35.0, saldoReal = 0.0), 0.0)
        assertEquals(0.0, ajusteDeSaldo(saldoNoApp = 99.999999999, saldoReal = 100.0), 0.0)
    }
}
