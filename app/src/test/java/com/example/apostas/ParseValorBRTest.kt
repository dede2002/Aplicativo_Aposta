package com.example.apostas

import com.example.apostas.util.parseValorBR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseValorBRTest {

    private val delta = 0.0001

    @Test
    fun virgulaDecimal() = assertEquals(1234.56, parseValorBR("1234,56")!!, delta)

    @Test
    fun milharEVirgula() = assertEquals(1234.56, parseValorBR("1.234,56")!!, delta)

    @Test
    fun pontoDecimal() = assertEquals(1.85, parseValorBR("1.85")!!, delta)

    @Test
    fun variosPontosDeMilhar() = assertEquals(1234567.0, parseValorBR("1.234.567")!!, delta)

    @Test
    fun comSimboloEEspacos() = assertEquals(50.0, parseValorBR(" R$ 50 ")!!, delta)

    @Test
    fun negativo() = assertEquals(-12.5, parseValorBR("-12,50")!!, delta)

    @Test
    fun vazioOuInvalido() {
        assertNull(parseValorBR(""))
        assertNull(parseValorBR("abc"))
        assertNull(parseValorBR("1,2,3"))
    }
}
