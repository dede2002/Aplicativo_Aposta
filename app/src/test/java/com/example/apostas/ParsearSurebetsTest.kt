package com.example.apostas

import com.example.apostas.ui.MensagemTelegram
import com.example.apostas.ui.parsearEmSurebets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ParsearSurebetsTest {

    private val separador = MensagemTelegram("", isSticker = true, stickerUniqueId = "AgAD7QUAAlP8wEQ")

    private fun unix(dia: Int, mes: Int, ano: Int): Long =
        Calendar.getInstance().apply { set(ano, mes - 1, dia, 12, 0, 0) }.timeInMillis / 1000

    private fun bloco(partida: String, lucro: String, data: Long) = listOf(
        MensagemTelegram("APOSTA PRINCIPAL\nPartida: $partida\nCasa: Bet365", false, dataUnix = data),
        MensagemTelegram("PROTEÇÃO DA APOSTA\nCasa: Betano", false, dataUnix = data),
        MensagemTelegram("LUCRO DA OPERAÇÃO\nLucro Líquido: R$ $lucro", false, dataUnix = data)
    )

    @Test
    fun cadaBlocoUsaADataDaPropriaMensagem() {
        val mensagens = bloco("Time A x Time B", "12,50", unix(10, 3, 2026)) +
                separador +
                bloco("Time C x Time D", "1.234,00", unix(11, 3, 2026))

        val previas = parsearEmSurebets(mensagens)

        assertEquals(2, previas.size)
        assertEquals("10/03/2026", previas[0].data)
        assertEquals("11/03/2026", previas[1].data)
        assertEquals(listOf("Bet365", "Betano"), previas[0].casas)
        assertEquals(12.5, previas[0].lucro, 0.0001)
        assertEquals(1234.0, previas[1].lucro, 0.0001)
    }

    @Test
    fun semDataUsaHoje() {
        val hoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date())
        val previas = parsearEmSurebets(bloco("X x Y", "5,00", 0L))
        assertEquals(hoje, previas.single().data)
    }
}
