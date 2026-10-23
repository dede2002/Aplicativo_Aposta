package com.example.apostas.data

import androidx.room.withTransaction

// Saldo livre de uma casa para fazer uma aposta nova:
// depósitos − saques + lucros já resolvidos − o que está preso em apostas em aberto nessa casa
// (stake das apostas de uma casa e stake/responsabilidade das pernas das surebets novas).
fun saldoParaApostar(
    casa: String,
    apostas: List<Aposta>,
    depositos: List<DepositoManual>,
    saques: List<Saque>
): Double {
    val entradas = depositos.filter { it.casa == casa }.sumOf { it.valor }
    val saidas = saques.filter { it.casa == casa }.sumOf { it.valor }
    val lucros = apostas.filter { it.lucro != 0.0 }.sumOf { it.lucroPorCasa[casa] ?: 0.0 }
    val emAberto = apostas.filter { it.lucro == 0.0 }.sumOf { aposta ->
        when {
            aposta.stakesNasCasas -> aposta.pernas.filter { it.casa == casa }.sumOf { it.perda() }
            aposta.casas.size <= 1 && aposta.casa == casa -> aposta.valor
            else -> 0.0
        }
    }
    return entradas - saidas + lucros - emAberto
}

// Depósitos automáticos para cada casa da surebet ter saldo para a sua stake (ou responsabilidade no Lay),
// como já acontece com a aposta de uma casa. `apostas` são as que já existiam (sem a surebet nova).
fun depositosParaStakes(
    surebet: Aposta,
    apostas: List<Aposta>,
    depositos: List<DepositoManual>,
    saques: List<Saque>
): Map<String, Double> {
    if (!surebet.stakesNasCasas) return emptyMap()
    return surebet.pernas.groupBy { it.casa }
        .mapValues { (casa, pernas) ->
            // Em centavos: a soma de muitos Double deixava sobras como 794,8700000000065
            Math.round((pernas.sumOf { it.perda() } - saldoParaApostar(casa, apostas, depositos, saques)) * 100) / 100.0
        }
        .filterValues { it > 0.0 }
}

// Grava a surebet nova e, se ela tem as stakes nas casas, os depósitos automáticos que faltam
// (ligados à surebet, para serem desfeitos se ela for excluída ainda em aberto).
// Retorna -1 se a operação do Telegram já existia (nada é gravado).
suspend fun AppDatabase.salvarSurebet(surebet: Aposta): Long = withTransaction {
    val faltando = if (surebet.stakesNasCasas) {
        depositosParaStakes(surebet, apostaDao().getAll(), depositoDao().getAll(), saqueDao().getAll())
    } else emptyMap()
    val id = apostaDao().insertSeNova(surebet)
    if (id != -1L) {
        faltando.forEach { (casa, valor) ->
            depositoDao().inserir(DepositoManual(casa = casa, valor = valor, apostaId = id.toInt()))
        }
    }
    id
}

// Exclui uma aposta. Resolvida: o dinheiro ganho ou perdido continua nas casas (depósito de compensação,
// decisão do dono). Surebet nova ainda em aberto: desfaz os depósitos automáticos das stakes dela.
suspend fun AppDatabase.excluirAposta(aposta: Aposta) = withTransaction {
    apostaDao().delete(aposta)
    if (aposta.lucro != 0.0) {
        aposta.lucroPorCasa.forEach { (conta, parte) -> depositoDao().inserir(DepositoManual(casa = conta, valor = parte)) }
    } else if (aposta.stakesNasCasas) {
        depositoDao().apagarDaAposta(aposta.id)
    }
}
