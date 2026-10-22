package com.example.apostas.telegram

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Recebe o push do servidor do Tips ("chegou uma entrada nova") e importa na hora.
class PushTipsService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["tipo"] == "nova_entrada") {
            ImportacaoAutomatica.importarPorPush(applicationContext)
        }
    }

    // O Firebase troca o token de vez em quando (reinstalação, limpeza de dados…): avisa o servidor.
    override fun onNewToken(token: String) {
        val aparelho = carregarAparelho(applicationContext) ?: return
        runCatching { ServidorTips(aparelho).registrarPush(token) }
    }
}

object PushDoAparelho {

    // O push só funciona se o app foi compilado com o google-services.json do Firebase.
    fun firebaseDisponivel(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private suspend fun tokenFcm(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    // Manda ao servidor o token de push deste celular. Seguro chamar sempre.
    // Retorna uma mensagem de erro para mostrar, ou null se deu certo.
    suspend fun registrar(context: Context): String? {
        val aparelho = carregarAparelho(context) ?: return "Conecte este celular ao Telegram primeiro."
        if (!firebaseDisponivel(context)) return "Este app foi compilado sem o Firebase (google-services.json)."
        val token = tokenFcm() ?: return "Não foi possível ativar as notificações instantâneas. Verifique a internet."
        return withContext(Dispatchers.IO) {
            try {
                ServidorTips(aparelho).registrarPush(token)
                null
            } catch (e: TelegramException) {
                e.message
            } catch (e: Exception) {
                "Sem conexão com o servidor."
            }
        }
    }
}
