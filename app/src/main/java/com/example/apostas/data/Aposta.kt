package com.example.apostas.data

import androidx.room.ColumnInfo
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
    val origemTelegram: String? = null,
    // O que foi apostado em cada casa (JSON de Perna, ver Pernas.kt). Só nas surebets importadas do Telegram.
    val pernasJson: String? = null,
    // Surebet salva a partir da versão 11 com stake e odd de todas as casas: a stake de cada casa foi
    // coberta no saldo dela (depósito automático do que faltava) e, no Green, o saldo de cada casa segue
    // as pernas (ver movimentoPorPerna). False nas surebets antigas, que continuam com a regra de antes.
    @ColumnInfo(defaultValue = "0")
    val stakesNasCasas: Boolean = false
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
// Surebet Green com stake e odd de cada casa: cada casa recebe o que aconteceu nela de verdade (ver movimentoPorPerna).
val Aposta.lucroPorCasa: Map<String, Double>
    get() {
        movimentoPorPerna()?.let { return it }
        val contas = casasDoResultado
        return contas.associateWith { lucro / contas.size }
    }

// Quanto o saldo de cada casa muda numa surebet Green, a partir das pernas:
// - casa que perdeu: perde a stake (Back) ou a responsabilidade, stake × (odd − 1) (Lay);
// - casa onde bateu: ganha o resto, de modo que a soma de todas as casas seja o lucro da operação
//   (Back 100 @ 2,5 que bate → +150; isso já desconta a comissão da exchange e arredondamentos).
// Como a stake foi coberta no saldo ao salvar, a casa onde bateu termina com o retorno inteiro
// (0 + depósito automático de 100 + 150 = 250).
// No Duplo Green o ganho é dividido entre as casas marcadas na proporção do ganho de cada perna.
// Null (usa a regra antiga) em surebets antigas (sem stakesNasCasas), em Red ou se faltar a perna da casa.
private fun Aposta.movimentoPorPerna(): Map<String, Double>? {
    if (!stakesNasCasas || lucro <= 0.0 || casaLucro == null) return null
    val pernas = pernas
    if (!pernasCompletas(pernas)) return null
    val vencedoras = casasDoResultado
    if (vencedoras.any { casa -> pernas.none { it.casa == casa } }) return null

    val (ganharam, perderam) = pernas.partition { it.casa in vencedoras }
    val movimento = mutableMapOf<String, Double>()
    perderam.forEach { movimento[it.casa] = (movimento[it.casa] ?: 0.0) - it.perda() }

    val ganhoTotal = lucro + perderam.sumOf { it.perda() }
    val somaGanhos = ganharam.sumOf { it.ganho() }
    ganharam.forEach { p ->
        val parte = if (somaGanhos > 0.0) ganhoTotal * p.ganho() / somaGanhos else ganhoTotal / ganharam.size
        movimento[p.casa] = (movimento[p.casa] ?: 0.0) + parte
    }
    return movimento
}

data class LucroPorDia(
    val data: String,
    val lucro: Double
)

