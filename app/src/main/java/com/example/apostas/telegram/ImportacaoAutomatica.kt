package com.example.apostas.telegram

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.apostas.R
import com.example.apostas.data.Aposta
import com.example.apostas.ui.CadastroApostaActivity
import java.util.Locale
import java.util.concurrent.TimeUnit

// Roda a importação em segundo plano. O Android executa a cada ~15 minutos no mínimo
// (pode atrasar para poupar bateria) e só com internet.
// Também roda na hora, quando chega o push do modo instantâneo (ImportacaoAutomatica.importarPorPush).
class ImportacaoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val resultado = ImportadorTelegram.importar(applicationContext)
        val origem = if (inputData.getBoolean(CHAVE_PUSH, false)) "⚡ push · " else ""
        salvarExecucaoSegundoPlano(
            applicationContext, System.currentTimeMillis(), origem + ImportadorTelegram.resumir(resultado)
        )
        NotificacoesImportacao.notificar(applicationContext, resultado.importadas)
        return if (resultado.falhaDeRede) Result.retry() else Result.success()
    }

    // Trabalho "expedited" no Android 11 ou mais antigo roda como serviço em primeiro plano
    // e precisa de uma notificação enquanto executa (no 12+ isso não é usado).
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(ID_NOTIFICACAO_TRABALHANDO, NotificacoesImportacao.notificacaoTrabalhando(applicationContext))

    companion object {
        const val CHAVE_PUSH = "por_push"
        private const val ID_NOTIFICACAO_TRABALHANDO = -2
    }
}

object ImportacaoAutomatica {
    private const val NOME_TAREFA = "importacao_telegram"
    private const val NOME_TAREFA_PUSH = "importacao_push"

    // Push do servidor: importa já, com prioridade. Se outra importação estiver rodando,
    // esta entra na fila logo depois (a mensagem nova pode ter chegado durante a anterior).
    fun importarPorPush(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<ImportacaoWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(ImportacaoWorker.CHAVE_PUSH to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOME_TAREFA_PUSH, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    // Seguro chamar sempre: se já estiver agendada, mantém a existente.
    fun agendar(context: Context) {
        val pedido = PeriodicWorkRequestBuilder<ImportacaoWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NOME_TAREFA, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }
}

object SegundoPlano {
    // true se o Tips está fora da otimização de bateria ("Sem restrições").
    // Sem isso o Android pode adiar a tarefa de ~15 min por horas.
    fun semRestricaoDeBateria(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // Abre o pedido do sistema para tirar o Tips da otimização de bateria.
    @SuppressLint("BatteryLife") // app pessoal, fora da Play Store; sem isso a importação atrasa
    fun pedirSemRestricao(context: Context) {
        val pedido = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(pedido)
        } catch (e: ActivityNotFoundException) {
            // Alguns aparelhos não têm o atalho: abre a lista geral de otimização de bateria
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

object NotificacoesImportacao {
    private const val CANAL = "importacao_telegram"

    fun podeNotificar(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    // Notificação de exemplo, para conferir se o celular está mostrando as do Tips.
    fun notificarTeste(context: Context): Boolean {
        if (!podeNotificar(context)) return false
        criarCanal(context)
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_surebet)
            .setContentTitle("Teste do Tips")
            .setContentText("As notificações de surebets importadas vão aparecer assim.")
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(ID_TESTE, notificacao)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private const val ID_TESTE = -1

    // Notificação discreta enquanto a importação roda como serviço (só Android 11 ou mais antigo).
    fun notificacaoTrabalhando(context: Context): android.app.Notification {
        criarCanal(context)
        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_surebet)
            .setContentTitle("Importando surebet do Telegram…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun notificar(context: Context, importadas: List<Aposta>) {
        if (importadas.isEmpty()) return
        if (!podeNotificar(context)) return

        criarCanal(context)
        val gerenciador = NotificationManagerCompat.from(context)
        importadas.forEach { aposta ->
            // Tocar abre a edição da aposta (útil para preencher o lucro, se não foi lido)
            val intent = Intent(context, CadastroApostaActivity::class.java)
                .putExtra("aposta_id", aposta.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendente = PendingIntent.getActivity(
                context, aposta.id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val jogo = aposta.descricao.removePrefix("Surebet ✅").removePrefix(" - ").ifBlank { aposta.casa }
            val texto = if (aposta.valor > 0.0)
                "Lucro R$ " + String.format(Locale("pt", "BR"), "%,.2f", aposta.valor) + " · " + aposta.casa
            else
                "Lucro não identificado. Toque para preencher."

            val notificacao = NotificationCompat.Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_notificacao_surebet)
                .setContentTitle("Surebet importada: $jogo")
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setContentIntent(pendente)
                .setAutoCancel(true)
                .build()
            try {
                gerenciador.notify(aposta.id, notificacao)
            } catch (e: SecurityException) {
                // Permissão retirada entre a checagem e o envio: só não notifica.
            }
        }
    }

    private fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Importação do Telegram", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Avisa quando uma surebet encaminhada ao bot é salva" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
