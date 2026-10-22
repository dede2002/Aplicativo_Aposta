package com.example.apostas.telegram

import android.content.Context
import androidx.core.content.edit

// Tudo do Telegram fica em telegram_prefs, que está fora do backup do Google
// (res/xml/backup_rules.xml e data_extraction_rules.xml).
private const val PREFS = "telegram_prefs"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

// Versões antigas guardavam o token do bot e a senha do servidor no celular.
// Agora tudo passa pelo servidor com a chave do aparelho, então esses dados são apagados.
fun apagarConfiguracaoAntiga(context: Context) {
    prefs(context).edit {
        remove("bot_token")
        remove("chat_id")
        remove("servidor_url")
        remove("servidor_chave")
    }
}

// Último update do Telegram já guardado no aparelho (no banco ou no buffer).
fun carregarUltimoUpdateId(context: Context): Long =
    prefs(context).getLong("ultimo_update_id", 0L)

fun salvarUltimoUpdateId(context: Context, updateId: Long) {
    prefs(context).edit { putLong("ultimo_update_id", updateId) }
}

// Mensagens de operações ainda incompletas (esperando o print do Resumo), em JSON.
fun carregarBufferJson(context: Context): String? = prefs(context).getString("buffer_mensagens", null)

fun salvarBufferJson(context: Context, json: String) {
    prefs(context).edit { putString("buffer_mensagens", json) }
}

// Resumo da última importação, para a tela do Telegram.
fun salvarUltimaImportacao(context: Context, quando: Long, resumo: String) {
    prefs(context).edit {
        putLong("ultima_importacao_quando", quando)
        putString("ultima_importacao_resumo", resumo)
    }
}

// Última vez que a tarefa em segundo plano rodou (separado da importação ao abrir o app),
// para saber se o Android está deixando ela executar.
fun salvarExecucaoSegundoPlano(context: Context, quando: Long, resumo: String) {
    prefs(context).edit {
        putLong("segundo_plano_quando", quando)
        putString("segundo_plano_resumo", resumo)
    }
}

fun carregarExecucaoSegundoPlano(context: Context): Pair<Long, String> {
    val p = prefs(context)
    return Pair(p.getLong("segundo_plano_quando", 0L), p.getString("segundo_plano_resumo", "") ?: "")
}

fun carregarUltimaImportacao(context: Context): Pair<Long, String> {
    val p = prefs(context)
    return Pair(p.getLong("ultima_importacao_quando", 0L), p.getString("ultima_importacao_resumo", "") ?: "")
}
