package com.example.apostas

import com.example.apostas.data.Aposta
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
}
