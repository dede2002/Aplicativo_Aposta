package com.example.apostas.telegram

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.apostas.R
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Freebet
import com.example.apostas.data.diasParaVencer
import com.example.apostas.data.freebetsVencendo
import com.example.apostas.ui.FreebetsActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Aviso de freebet disponível que vence hoje ou amanhã, para não perder o crédito.
// Roda junto da tarefa de segundo plano (ImportacaoWorker): no máximo uma notificação por dia, a partir das 9h.
object LembreteFreebets {

    private const val HORA_MINIMA = 9
    private const val ID_NOTIFICACAO = -4

    suspend fun verificar(context: Context) {
        val agora = Calendar.getInstance()
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(agora.time)
        val prefs = context.getSharedPreferences("lembretes", Context.MODE_PRIVATE)
        if (agora.get(Calendar.HOUR_OF_DAY) < HORA_MINIMA) return
        if (prefs.getString("freebets_ultimo_dia", null) == hoje) return

        val vencendo = freebetsVencendo(AppDatabase.getDatabase(context).freebetDao().disponiveis(), agora.time)
        prefs.edit { putString("freebets_ultimo_dia", hoje) }
        if (vencendo.isEmpty() || !NotificacoesImportacao.podeNotificar(context)) return
        notificar(context, vencendo)
    }

    private fun notificar(context: Context, vencendo: List<Freebet>) {
        NotificacoesImportacao.criarCanal(context)
        val intent = Intent(context, FreebetsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendente = PendingIntent.getActivity(
            context, ID_NOTIFICACAO, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = if (vencendo.size == 1) "🎁 Freebet vencendo" else "🎁 ${vencendo.size} freebets vencendo"
        val estilo = NotificationCompat.InboxStyle()
        vencendo.forEach { fb ->
            val quando = if (fb.diasParaVencer() == 0L) "vence hoje" else "vence amanhã"
            estilo.addLine("${fb.casa} · " + String.format(Locale("pt", "BR"), "R$ %,.2f", fb.valor) + " · $quando")
        }

        val notificacao = NotificationCompat.Builder(context, NotificacoesImportacao.CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_surebet)
            .setColor(NotificacoesImportacao.COR_TIPS)
            .setContentTitle(titulo)
            .setContentText("Use numa surebet antes de perder o crédito.")
            .setStyle(estilo.setBigContentTitle(titulo))
            .setContentIntent(pendente)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_NOTIFICACAO, notificacao)
        } catch (e: SecurityException) {
            // Permissão retirada: só não avisa
        }
    }
}
