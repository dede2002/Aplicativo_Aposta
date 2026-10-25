package com.example.apostas.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Cálculos da tela "Relatório" (Estatísticas → Relatório): lucro por período, por casa/conta, por semana e por mês.
// Conta só apostas resolvidas (lucro ≠ 0), pela data da aposta. A Banca Total e o Lucro Diário são contadores à parte
// (podem ter sido editados à mão) e não entram aqui.

private val FORMATO_DATA = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }

fun dataDaAposta(aposta: Aposta): Date? = runCatching { FORMATO_DATA.parse(aposta.data) }.getOrNull()

private fun Aposta.isSurebet() = descricao.startsWith("Surebet ✅")
private fun Aposta.isCassino() = descricao.startsWith("Cassino ♠️")

// Surebet de extração de freebet: alguma casa foi feita com freebet
fun Aposta.usouFreebet(): Boolean = isSurebet() && pernas.any { it.isFreebet() }

// Quanto saiu do bolso para fazer a aposta, quando o app sabe: aposta comum = o valor;
// surebet com valor e odd de cada casa = a soma do que cada casa arriscou. Null quando não dá para saber
// (surebet antiga ou cassino, em que `valor` guarda o lucro).
fun valorApostado(aposta: Aposta): Double? = when {
    aposta.isCassino() -> null
    aposta.isSurebet() -> aposta.pernas.takeIf { pernasCompletas(it) }?.sumOf { it.perda() }
    else -> aposta.valor
}

// Resolvidas cuja data está entre inicio e fim (inclusive, pelo dia); null = sem limite
fun resolvidasNoPeriodo(apostas: List<Aposta>, inicio: Date?, fim: Date?): List<Aposta> {
    val de = inicio?.let { inicioDoDia(it) }
    val ate = fim?.let { inicioDoDia(it) }
    return apostas.filter { aposta ->
        if (aposta.lucro == 0.0) return@filter false
        val dia = dataDaAposta(aposta)?.let { inicioDoDia(it) } ?: return@filter (de == null && ate == null)
        (de == null || dia >= de) && (ate == null || dia <= ate)
    }
}

data class ResumoRelatorio(
    val lucro: Double,
    val greens: Int,
    val reds: Int,
    val lucroSurebet: Double,
    val lucroCassino: Double,
    val lucroFreebets: Double,
    // Retorno sobre o valor apostado, só com as apostas em que o app sabe quanto foi apostado
    val apostado: Double,
    val lucroDasApostasComValor: Double
) {
    val operacoes: Int get() = greens + reds
    val retornoPercentual: Double? get() = if (apostado > 0) lucroDasApostasComValor / apostado * 100 else null
}

fun resumoDoPeriodo(resolvidas: List<Aposta>): ResumoRelatorio {
    val comValor = resolvidas.mapNotNull { a -> valorApostado(a)?.let { a to it } }
    return ResumoRelatorio(
        lucro = centavos(resolvidas.sumOf { it.lucro }),
        greens = resolvidas.count { it.lucro > 0 },
        reds = resolvidas.count { it.lucro < 0 },
        lucroSurebet = centavos(resolvidas.filter { it.isSurebet() }.sumOf { it.lucro }),
        lucroCassino = centavos(resolvidas.filter { it.isCassino() }.sumOf { it.lucro }),
        lucroFreebets = centavos(resolvidas.filter { it.usouFreebet() }.sumOf { it.lucro }),
        apostado = centavos(comValor.sumOf { it.second }),
        lucroDasApostasComValor = centavos(comValor.sumOf { it.first.lucro })
    )
}

// O que entrou ou saiu de cada casa/conta (a mesma divisão usada nos saldos), do maior para o menor.
// Surebets antigas, anotadas antes de o app perguntar a casa do resultado, guardam todas as casas num texto só
// ("Bet365 Sporty Betano"): não dá para dizer quanto foi de cada casa, então ficam de fora (pedido do dono).
fun resultadoPorCasa(resolvidas: List<Aposta>): List<Pair<String, Double>> {
    // "Bet365" e "Bet365 " (espaço sobrando em anotação antiga) ou "bet365" são a mesma casa: uma linha só,
    // com o nome como apareceu primeiro
    val porCasa = mutableMapOf<String, Double>()
    val nomes = mutableMapOf<String, String>()
    resolvidas.filterNot { it.isSurebet() && it.casaLucro == null }.forEach { a ->
        a.lucroPorCasa.forEach { (casa, v) ->
            val chave = casa.trim().lowercase()
            nomes.getOrPut(chave) { casa.trim() }
            porCasa[chave] = (porCasa[chave] ?: 0.0) + v
        }
    }
    return porCasa.map { (chave, v) -> nomes.getValue(chave) to centavos(v) }
        .filter { it.second != 0.0 }
        .sortedByDescending { it.second }
}

// Lucro por mês ("10/2026"), do mais recente para o mais antigo
fun lucroPorMes(resolvidas: List<Aposta>): List<Pair<String, Double>> =
    resolvidas.mapNotNull { a -> dataDaAposta(a)?.let { d -> Calendar.getInstance().apply { time = d } to a.lucro } }
        .groupBy { (c, _) -> c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) }
        .toSortedMap(compareByDescending { it })
        .map { (chave, itens) -> "%02d/%04d".format(chave % 100 + 1, chave / 100) to centavos(itens.sumOf { it.second }) }

// Lucro por semana (de domingo a sábado), rotulada pelo domingo ("Semana de 19/10"), da mais recente para a mais antiga
fun lucroPorSemana(resolvidas: List<Aposta>): List<Pair<String, Double>> {
    val rotulo = SimpleDateFormat("dd/MM", Locale.getDefault())
    return resolvidas.mapNotNull { a -> dataDaAposta(a)?.let { inicioDaSemana(it) to a.lucro } }
        .groupBy { it.first }
        .toSortedMap(compareByDescending { it })
        .map { (inicio, itens) -> "Semana de " + rotulo.format(Date(inicio)) to centavos(itens.sumOf { it.second }) }
}

private fun inicioDoDia(data: Date): Long = Calendar.getInstance().apply {
    time = data
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun inicioDaSemana(data: Date): Long = Calendar.getInstance().apply {
    timeInMillis = inicioDoDia(data)
    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    if (timeInMillis > inicioDoDia(data)) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis

private fun centavos(valor: Double): Double = Math.round(valor * 100) / 100.0
