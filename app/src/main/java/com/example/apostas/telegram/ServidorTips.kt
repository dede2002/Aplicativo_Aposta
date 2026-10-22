package com.example.apostas.telegram

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Servidor do Tips (pasta servidor/ do projeto). Todo o Telegram passa por ele: o app nunca
// conhece o token do bot. Cada celular tem uma chave própria, criada na conexão.
const val SERVIDOR_URL = "https://tips-servidor.tips-servidor.workers.dev"

// Mensagem de erro já pronta para mostrar ao usuário.
class TelegramException(mensagem: String) : Exception(mensagem)

private const val PREFS = "telegram_prefs"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

data class Aparelho(val id: String, val chave: String)

fun carregarAparelho(context: Context): Aparelho? {
    val p = prefs(context)
    val id = p.getString("aparelho_id", "") ?: ""
    val chave = p.getString("aparelho_chave", "") ?: ""
    return if (id.isNotBlank() && chave.isNotBlank()) Aparelho(id, chave) else null
}

private fun salvarAparelho(context: Context, aparelho: Aparelho) {
    prefs(context).edit {
        putString("aparelho_id", aparelho.id)
        putString("aparelho_chave", aparelho.chave)
    }
}

// true quando este celular já foi ligado a uma conversa com o bot
fun aparelhoConectado(context: Context): Boolean =
    carregarAparelho(context) != null && prefs(context).getBoolean("aparelho_conectado", false)

fun salvarEstadoConexao(context: Context, conectado: Boolean, nome: String?, bot: String?) {
    prefs(context).edit {
        putBoolean("aparelho_conectado", conectado)
        putString("aparelho_nome", nome)
        if (bot != null) putString("bot_nome", bot)
    }
}

fun carregarNomeConectado(context: Context): String? = prefs(context).getString("aparelho_nome", null)

fun carregarNomeDoBot(context: Context): String? = prefs(context).getString("bot_nome", null)

fun esquecerAparelho(context: Context) {
    prefs(context).edit {
        remove("aparelho_id")
        remove("aparelho_chave")
        remove("aparelho_conectado")
        remove("aparelho_nome")
    }
}

data class EstadoAparelho(val conectado: Boolean, val nome: String?, val bot: String?, val push: Boolean)

class ServidorTips(private val aparelho: Aparelho?) {

    companion object {
        // Cria este aparelho no servidor e guarda a chave. Retorna o link de conexão do Telegram.
        fun criarAparelho(context: Context): String {
            val r = JSONObject(chamar("POST", "/aparelhos", null, "{}"))
            salvarAparelho(context, Aparelho(r.getString("id"), r.getString("chave")))
            return r.getString("link")
        }

        private fun chamar(metodo: String, caminho: String, aparelho: Aparelho?, corpo: String? = null): String =
            String(chamarBytes(metodo, caminho, aparelho, corpo), Charsets.UTF_8)

        private fun chamarBytes(metodo: String, caminho: String, aparelho: Aparelho?, corpo: String? = null): ByteArray {
            val conexao = URL(SERVIDOR_URL + caminho).openConnection() as HttpURLConnection
            conexao.requestMethod = metodo
            conexao.connectTimeout = 10_000
            conexao.readTimeout = 20_000
            aparelho?.let { conexao.setRequestProperty("Authorization", "Bearer ${it.id}.${it.chave}") }
            if (corpo != null) {
                conexao.doOutput = true
                conexao.setRequestProperty("Content-Type", "application/json")
                conexao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            }
            try {
                return when (val codigo = conexao.responseCode) {
                    HttpURLConnection.HTTP_OK -> conexao.inputStream.use { it.readBytes() }
                    HttpURLConnection.HTTP_UNAUTHORIZED ->
                        throw TelegramException("Este celular não está mais conectado. Toque em “Conectar com o Telegram”.")
                    HttpURLConnection.HTTP_CONFLICT ->
                        throw TelegramException("Termine a conexão: abra o link do Telegram e toque em Iniciar.")
                    else -> throw TelegramException("O servidor respondeu com erro $codigo. Tente novamente.")
                }
            } finally {
                conexao.disconnect()
            }
        }
    }

    private fun exigirAparelho() = aparelho ?: throw TelegramException("Conecte este celular ao Telegram primeiro.")

    // Novo link de conexão (o código anterior expira em 15 minutos)
    fun novoLink(): String = JSONObject(chamar("POST", "/aparelho/codigo", exigirAparelho(), "{}")).getString("link")

    fun estado(): EstadoAparelho {
        val r = JSONObject(chamar("GET", "/aparelho", exigirAparelho()))
        return EstadoAparelho(
            conectado = r.optBoolean("conectado"),
            nome = r.optString("nome", "").ifBlank { null }.takeUnless { r.isNull("nome") },
            bot = r.optString("bot", "").ifBlank { null },
            push = r.optBoolean("push")
        )
    }

    fun registrarPush(tokenFcm: String) {
        chamar("POST", "/aparelho/fcm", exigirAparelho(), JSONObject().put("token", tokenFcm).toString())
    }

    fun desconectar() {
        chamar("DELETE", "/aparelho", exigirAparelho())
    }

    // Mesmo formato do getUpdates do Telegram, só com as mensagens desta pessoa.
    // "after" = último update já guardado neste aparelho.
    fun getUpdates(after: Long, limite: Int = 100): JSONArray =
        JSONObject(chamar("GET", "/updates?after=$after&limit=$limite", exigirAparelho())).getJSONArray("result")

    // Print de uma mensagem desta pessoa (o servidor baixa do Telegram)
    fun baixarFoto(fileId: String): ByteArray =
        chamarBytes("GET", "/foto?file_id=${URLEncoder.encode(fileId, "UTF-8")}", exigirAparelho())
}
