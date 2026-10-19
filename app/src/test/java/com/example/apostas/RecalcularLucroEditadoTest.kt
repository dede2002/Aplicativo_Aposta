package com.example.apostas

import com.example.apostas.ui.recalcularLucroEditado
import org.junit.Assert.assertEquals
import org.junit.Test

class RecalcularLucroEditadoTest {

    private val delta = 0.0001

    @Test
    fun apostaEmAberto_continuaZerada() {
        assertEquals(0.0, recalcularLucroEditado("Flamengo vencer", 100.0, 150.0, 0.0), delta)
    }

    @Test
    fun apostaGreen_usaRetornoMenosValor() {
        // R$ 100 na odd 1,50 → lucro 50
        assertEquals(50.0, recalcularLucroEditado("Flamengo vencer", 100.0, 150.0, 30.0), delta)
    }

    @Test
    fun apostaRed_perdeAStakeInteira() {
        // Antes ficava -50 (só batia na odd 2,00)
        assertEquals(-100.0, recalcularLucroEditado("Flamengo vencer", 100.0, 150.0, -80.0), delta)
    }

    @Test
    fun surebetGreen_mantemOLucroDigitado() {
        // Surebet guarda o lucro em valor, com odd 1,0; antes virava 0 (Em aberto)
        assertEquals(25.0, recalcularLucroEditado("Surebet ✅ - Jogo", 25.0, 25.0, 20.0), delta)
    }

    @Test
    fun cassinoGreen_mantemOLucroDigitado() {
        assertEquals(40.0, recalcularLucroEditado("Cassino ♠️ - Tigrinho", 40.0, 40.0, 10.0), delta)
    }

    @Test
    fun cassinoRed_ficaNegativo() {
        assertEquals(-40.0, recalcularLucroEditado("Cassino ♠️", 40.0, 40.0, -10.0), delta)
    }

    @Test
    fun duploGreen_mantemOLucroTotalDigitado() {
        // Surebet com lucro cadastrado de 25, mas o duplo green rendeu 180
        assertEquals(180.0, recalcularLucroEditado("Surebet ✅", 25.0, 25.0, 180.0, resultadoDuplo = true), delta)
    }

    @Test
    fun duploRed_mantemOPrejuizoTotalDigitado() {
        assertEquals(-60.0, recalcularLucroEditado("Surebet ✅", 25.0, 25.0, -60.0, resultadoDuplo = true), delta)
    }
}
