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
    val print: String? = null
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
                print = o.optString("print", "").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())
}

val Aposta.pernas: List<Perna>
    get() = pernasDeJson(pernasJson)

// Perna de uma casa (primeira que bater o nome), para mostrar ao lado da casa no diálogo de resultado
fun Aposta.pernaDaCasa(casa: String): Perna? = pernas.firstOrNull { it.casa == casa }
