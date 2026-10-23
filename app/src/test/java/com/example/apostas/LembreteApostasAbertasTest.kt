package com.example.apostas

import com.example.apostas.data.Aposta
import com.example.apostas.telegram.LembreteApostasAbertas.apostasAbertasAntigas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class LembreteApostasAbertasTest {

    private fun dia(d: Int, m: Int, a: Int, hora: Int = 12) =
        Calendar.getInstance().apply { set(a, m - 1, d, hora, 0, 0) }.time

    private fun aposta(id: Int, data: String, lucro: Double = 0.0) = Aposta(
        id = id, descricao = "Aposta $id", casa = "Bet365", valor = 10.0, odds = 2.0,
        retornoPotencial = 20.0, lucro = lucro, data = data
    )

    @Test
    fun avisaSoEmAbertoComCincoDiasOuMais_maisAntigasPrimeiro() {
        val hoje = dia(22, 10, 2026, hora = 9)
        val apostas = listOf(
            aposta(1, "18/10/2026"),            // 4 dias: ainda não
            aposta(2, "17/10/2026"),            // 5 dias: avisa
            aposta(3, "01/10/2026"),            // 21 dias: avisa
            aposta(4, "01/10/2026", lucro = 10.0), // resolvida: não
            aposta(5, "data inválida")          // ignora
        )
        assertEquals(listOf(3, 2), apostasAbertasAntigas(apostas, hoje).map { it.id })
    }
}
