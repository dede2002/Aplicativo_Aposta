package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.data.Perna
import com.example.apostas.data.pernasParaJson
import com.example.apostas.data.isDuploGreen
import com.example.apostas.data.isDuploRed
import com.example.apostas.data.lucroPorCasa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LucroPorCasaTest {

    private fun surebet(lucro: Double, casaLucro: String?) = Aposta(
        descricao = "Surebet ✅", casa = "Bet365 | Betano | Pinnacle",
        valor = 25.0, odds = 1.0, retornoPotencial = 25.0, lucro = lucro,
        data = "10/03/2026", casaLucro = casaLucro
    )

    @Test
    fun apostaDeUmaCasa_vaiInteiraParaAQuelaCasa() {
        val aposta = Aposta(
            descricao = "Flamengo vencer", casa = "Bet365", valor = 100.0, odds = 1.5,
            retornoPotencial = 150.0, lucro = 50.0, data = "10/03/2026"
        )
        assertEquals(mapOf("Bet365" to 50.0), aposta.lucroPorCasa)
        assertFalse(aposta.isDuploGreen)
    }

    @Test
    fun surebetGreen_vaiParaACasaEscolhida() {
        assertEquals(mapOf("Betano" to 25.0), surebet(25.0, "Betano").lucroPorCasa)
    }

    @Test
    fun duploGreen_divideIgualmenteEntreAsCasasMarcadas() {
        val aposta = surebet(180.0, "Bet365 | Pinnacle")
        assertTrue(aposta.isDuploGreen)
        assertEquals(mapOf("Bet365" to 90.0, "Pinnacle" to 90.0), aposta.lucroPorCasa)
    }

    @Test
    fun duploRed_divideOPrejuizoEntreAsCasasMarcadas() {
        val aposta = surebet(-60.0, "Bet365 | Betano | Pinnacle")
        assertTrue(aposta.isDuploRed)
        assertFalse(aposta.isDuploGreen)
        assertEquals(mapOf("Bet365" to -20.0, "Betano" to -20.0, "Pinnacle" to -20.0), aposta.lucroPorCasa)
    }

    @Test
    fun surebetAntiga_semCasaLucro_continuaNaContaCombinada() {
        assertEquals(mapOf("Bet365 | Betano | Pinnacle" to 25.0), surebet(25.0, null).lucroPorCasa)
    }

    // ── Surebet com stake e odd de cada casa ─────────────────────────

    private fun surebetComPernas(lucro: Double, casaLucro: String?, vararg pernas: Perna) = Aposta(
        descricao = "Surebet ✅ - Flamengo vs Vasco", casa = pernas.joinToString(" | ") { it.casa },
        valor = 10.0, odds = 1.0, retornoPotencial = 10.0, lucro = lucro,
        data = "10/03/2026", casaLucro = casaLucro, pernasJson = pernasParaJson(pernas.toList()),
        stakesNasCasas = true
    )

    private fun assertMapa(esperado: Map<String, Double>, atual: Map<String, Double>) {
        assertEquals(esperado.keys, atual.keys)
        esperado.forEach { (casa, valor) -> assertEquals(casa, valor, atual.getValue(casa), 0.001) }
    }

    @Test
    fun surebetBack_casaQueBateuRecebeOGanhoEAOutraPerdeAStake() {
        // Bet365: Flamengo 100 @ 2,5 (retorno 250). Betano: contra 140 @ 1,85 (retorno 259).
        val aposta = surebetComPernas(
            10.0, "Bet365",
            Perna("Bet365", tipo = "Back", odd = 2.5, stake = 100.0),
            Perna("Betano", tipo = "Back", odd = 1.85, stake = 140.0)
        )
        // Bet365: 250 − 100 = +150 (os 250 voltam, a stake já tinha saído). Betano: −140. Soma = lucro (10).
        assertMapa(mapOf("Bet365" to 150.0, "Betano" to -140.0), aposta.lucroPorCasa)
    }

    @Test
    fun surebetComLay_casaQueBateuNoLayRecebeAStakeDoLay() {
        val pernas = arrayOf(
            Perna("CassinoBet", tipo = "Back", odd = 2.0, stake = 100.0),
            Perna("BetBra", tipo = "Lay", odd = 2.05, stake = 97.0)
        )
        // Bateu na exchange (o lay ganhou): CassinoBet perde a stake, BetBra fica com o resto.
        assertMapa(
            mapOf("CassinoBet" to -100.0, "BetBra" to 102.0),
            surebetComPernas(2.0, "BetBra", *pernas).lucroPorCasa
        )
        // Bateu na CassinoBet: BetBra perde a responsabilidade 97 × 1,05 = 101,85.
        assertMapa(
            mapOf("CassinoBet" to 103.85, "BetBra" to -101.85),
            surebetComPernas(2.0, "CassinoBet", *pernas).lucroPorCasa
        )
    }

    @Test
    fun duploGreenComPernas_divideNaProporcaoDoGanhoDeCadaCasa() {
        val aposta = surebetComPernas(
            250.0, "Bet365 | Betano",
            Perna("Bet365", tipo = "Back", odd = 2.0, stake = 100.0),  // ganho 100
            Perna("Betano", tipo = "Back", odd = 3.0, stake = 50.0),   // ganho 100
            Perna("Pinnacle", tipo = "Back", odd = 4.0, stake = 50.0)  // perde 50
        )
        assertMapa(mapOf("Bet365" to 150.0, "Betano" to 150.0, "Pinnacle" to -50.0), aposta.lucroPorCasa)
    }

    @Test
    fun surebetSemStakeOuRed_continuaComARegraAntiga() {
        val semStake = surebetComPernas(
            25.0, "Bet365", Perna("Bet365", aposta = "Flamengo"), Perna("Betano", aposta = "Vasco")
        )
        assertEquals(mapOf("Bet365" to 25.0), semStake.lucroPorCasa)

        val red = surebetComPernas(
            -30.0, "Betano",
            Perna("Bet365", odd = 2.0, stake = 100.0), Perna("Betano", odd = 2.0, stake = 100.0)
        )
        assertEquals(mapOf("Betano" to -30.0), red.lucroPorCasa)
    }

    @Test
    fun surebetAnotadaAntesDaRegraNova_continuaComARegraAntiga() {
        val antiga = surebetComPernas(
            10.0, "Bet365",
            Perna("Bet365", odd = 2.5, stake = 100.0), Perna("Betano", odd = 1.85, stake = 140.0)
        ).copy(stakesNasCasas = false)
        assertEquals(mapOf("Bet365" to 10.0), antiga.lucroPorCasa)
    }
}
