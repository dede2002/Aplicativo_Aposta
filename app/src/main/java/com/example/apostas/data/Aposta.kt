package com.example.apostas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(
    tableName = "apostas",
    indices = [Index(value = ["origemTelegram"], unique = true)]
)
data class Aposta(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val descricao: String,
    val casa: String,
    val valor: Double,
    val odds: Double,
    val retornoPotencial: Double,
    val lucro: Double,
    val data: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
    // Em operações com várias casas (surebet), a casa onde o resultado caiu.
    // No Duplo Green guarda as casas que bateram, no mesmo formato de `casa` ("Bet365 | Betano").
    // Null em apostas de uma casa só, em apostas em aberto e em surebets antigas (antes da versão 8).
    val casaLucro: String? = null,
    // Identificador da mensagem do Telegram que gerou esta aposta (importação automática).
    // Único: a mesma operação nunca é importada duas vezes. Null em apostas cadastradas à mão.
    val origemTelegram: String? = null
)

private fun separarCasas(texto: String): List<String> =
    texto.split(" | ").map { it.trim() }.filter { it.isNotBlank() }

// Casas da aposta; surebets guardam várias em "Bet365 | Betano".
val Aposta.casas: List<String>
    get() = separarCasas(casa)

// Contas que recebem o lucro ou prejuízo desta aposta no saldo das casas.
// Sem casaLucro (aposta de uma casa ou surebet antiga), é a própria `casa`, sem separar.
val Aposta.casasDoResultado: List<String>
    get() = casaLucro?.let(::separarCasas)?.takeIf { it.isNotEmpty() } ?: listOf(casa)

// Resultado em mais de uma casa, com o valor total digitado pelo usuário (Duplo Green ou Duplo Red).
val Aposta.isResultadoDuplo: Boolean
    get() = lucro != 0.0 && casasDoResultado.size > 1

// Duplo Green: o lucro caiu em mais de uma casa.
val Aposta.isDuploGreen: Boolean
    get() = lucro > 0.0 && isResultadoDuplo

// Duplo Red: o prejuízo ficou em mais de uma casa.
val Aposta.isDuploRed: Boolean
    get() = lucro < 0.0 && isResultadoDuplo

// Parte do lucro (ou prejuízo) desta aposta que vai para o saldo de cada casa
// (dividido igualmente no Duplo Green / Duplo Red).
val Aposta.lucroPorCasa: Map<String, Double>
    get() {
        val contas = casasDoResultado
        return contas.associateWith { lucro / contas.size }
    }

data class LucroPorDia(
    val data: String,
    val lucro: Double
)

