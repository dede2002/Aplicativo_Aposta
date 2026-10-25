package com.example.apostas.data

import org.json.JSONArray
import org.json.JSONObject

// "Perna" = o que foi apostado em cada casa de uma surebet. Serve para o usuário saber,
// ao marcar Green/Red, qual casa bateu (ex.: CassinoBet "Ambas marcam: Sim", BetBra "Lay Ambas marcam: Sim").
// Guardado como JSON em Aposta.pernasJson (o texto fica para sempre; o print é apagado depois de um tempo).
data class Perna(
    val casa: String,
    // O que foi apostado, lido do print (ex.: "Ambas equipes marcam: Sim"); null se o OCR não achou
    val aposta: String? = null,
    // "Back" (a favor) ou "Lay" (contra), da tabela do Resumo
    val tipo: String? = null,
    val odd: Double? = null,
    val stake: Double? = null,
    // Nome do arquivo do print em filesDir/prints (pode já ter sido apagado pela limpeza)
    val print: String? = null,
    // Pernas com o mesmo grupo são o mesmo palpite dividido entre contas (outros CPFs): ganham ou perdem juntas.
    // Null = grupo próprio (ver grupoDa)
    val grupo: Int? = null,
    // Perna feita com uma freebet (id em Freebet): o valor não sai do saldo e não volta se bater
    val freebetId: Int? = null,
    // Quanto perdeu na missão para ganhar essa freebet (descontado do lucro da surebet, fora dos saldos)
    val custoFreebet: Double? = null
)

fun pernasParaJson(pernas: List<Perna>): String? {
    if (pernas.isEmpty()) return null
    val array = JSONArray()
    pernas.forEach { p ->
        array.put(JSONObject().apply {
            put("casa", p.casa)
            p.aposta?.let { put("aposta", it) }
            p.tipo?.let { put("tipo", it) }
            p.odd?.let { put("odd", it) }
            p.stake?.let { put("stake", it) }
            p.print?.let { put("print", it) }
            p.grupo?.let { put("grupo", it) }
            p.freebetId?.let { put("freebetId", it) }
            p.custoFreebet?.let { put("custoFreebet", it) }
        })
    }
    return array.toString()
}

fun pernasDeJson(json: String?): List<Perna> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Perna(
                casa = o.getString("casa"),
                aposta = o.optString("aposta", "").ifBlank { null },
                tipo = o.optString("tipo", "").ifBlank { null },
                odd = if (o.has("odd")) o.getDouble("odd") else null,
                stake = if (o.has("stake")) o.getDouble("stake") else null,
                print = o.optString("print", "").ifBlank { null },
                grupo = if (o.has("grupo")) o.getInt("grupo") else null,
                freebetId = if (o.has("freebetId")) o.getInt("freebetId") else null,
                custoFreebet = if (o.has("custoFreebet")) o.getDouble("custoFreebet") else null
            )
        }
    }.getOrDefault(emptyList())
}

val Aposta.pernas: List<Perna>
    get() = pernasDeJson(pernasJson)

// Perna de uma casa (primeira que bater o nome), para mostrar ao lado da casa no diálogo de resultado
fun Aposta.pernaDaCasa(casa: String): Perna? = pernas.firstOrNull { it.casa == casa }

// Todas as casas com stake e odd: dá para saber o que entra e sai de cada casa.
fun pernasCompletas(pernas: List<Perna>): Boolean =
    pernas.isNotEmpty() && pernas.all { (it.stake ?: 0.0) > 0.0 && (it.odd ?: 0.0) > 1.0 }

fun Perna.isLay() = tipo.equals("Lay", ignoreCase = true)

fun Perna.isFreebet() = freebetId != null

// Quanto sai da casa se esta perna perder: a stake (Back) ou a responsabilidade (Lay).
// É também o que a casa precisa ter de saldo para fazer a aposta. Freebet: nada sai do saldo.
fun Perna.perda(): Double = when {
    isFreebet() -> 0.0
    isLay() -> (stake ?: 0.0) * ((odd ?: 1.0) - 1)
    else -> stake ?: 0.0
}

// Quanto a casa ganha (além da stake de volta) se esta perna bater.
// Freebet também ganha valor × (odd − 1): o valor da freebet não volta.
fun Perna.ganho(): Double = if (isLay()) stake ?: 0.0 else (stake ?: 0.0) * ((odd ?: 1.0) - 1)

// Quanto se perdeu nas missões das freebets usadas nesta surebet (fica fora dos saldos)
fun custoDasFreebets(pernas: List<Perna>): Double = pernas.sumOf { it.custoFreebet ?: 0.0 }

// Grupo da perna de índice i: o salvo, ou um próprio (as pernas sem grupo nunca se juntam com as outras)
fun grupoDa(pernas: List<Perna>, i: Int): Int = pernas[i].grupo ?: (-1 - i)

// Lucro garantido pelas pernas: em cada cenário bate um grupo (as contas do mesmo palpite juntas) e as
// outras pernas perdem; o lucro garantido é o pior cenário. Não considera comissão da exchange.
// Null se faltar stake ou odd em alguma perna.
fun lucroGarantido(pernas: List<Perna>): Double? {
    if (!pernasCompletas(pernas)) return null
    val grupos = pernas.indices.map { grupoDa(pernas, it) }
    // Surebet precisa de pelo menos dois palpites (com uma casa só, "garantido" seria só o cenário de ganhar)
    if (grupos.distinct().size < 2) return null
    return grupos.distinct().minOf { g ->
        pernas.indices.sumOf { i -> if (grupos[i] == g) pernas[i].ganho() else -pernas[i].perda() }
    }
}

// Lucro da surebet depois de editar as pernas. O lucro salvo veio do Resumo (já com comissão e
// arredondamentos), então só a diferença que a edição causou é aplicada sobre ele.
// Se as pernas antigas não tinham stake e odd, usa o cálculo das novas; se as novas também não têm, mantém.
fun lucroAposEdicao(lucroAtual: Double, antigas: List<Perna>, novas: List<Perna>): Double {
    // Líquido: com freebet, já sem o que se perdeu na missão (tirar ou pôr a freebet muda o lucro)
    val novo = lucroLiquidoPelasPernas(novas) ?: return lucroAtual
    val antigo = lucroLiquidoPelasPernas(antigas)
    val resultado = if (antigo == null) novo else lucroAtual + (novo - antigo)
    return Math.round(resultado * 100) / 100.0
}

// Lucro da surebet pelas casas, já descontando o que se perdeu nas missões das freebets
// (o lucro garantido da extração − o custo das freebets), em centavos. Null se faltar valor ou odd.
fun lucroLiquidoPelasPernas(pernas: List<Perna>): Double? =
    lucroGarantido(pernas)?.let { Math.round((it - custoDasFreebets(pernas)) * 100) / 100.0 }
