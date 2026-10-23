package com.example.apostas.telegram

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.apostas.MainActivity
import com.example.apostas.R
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.FiltroAposta
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Lembrete diário de apostas esquecidas em aberto (5 dias ou mais depois da data da aposta).
// Ajuda a resolver ou excluir, e libera os prints guardados (que só são apagados de apostas resolvidas).
// Roda junto da tarefa de segundo plano (ImportacaoWorker): no máximo uma notificação por dia, a partir das 9h.
object LembreteApostasAbertas {

    const val DIAS_EM_ABERTO = 5
    private const val HORA_MINIMA = 9
    private const val ID_NOTIFICACAO = -3
    const val EXTRA_FILTRO = "filtro"

    suspend fun verificar(context: Context) {
        val agora = Calendar.getInstance()
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(agora.time)
        val prefs = context.getSharedPreferences("lembretes", Context.MODE_PRIVATE)
        if (agora.get(Calendar.HOUR_OF_DAY) < HORA_MINIMA) return
        if (prefs.getString("abertas_ultimo_dia", null) == hoje) return

        val antigas = apostasAbertasAntigas(AppDatabase.getDatabase(context).apostaDao().getAll(), agora.time)
        // Marca o dia mesmo sem nada para avisar: a conta é refeita amanhã
        prefs.edit { putString("abertas_ultimo_dia", hoje) }
        if (antigas.isEmpty() || !NotificacoesImportacao.podeNotificar(context)) return
        notificar(context, antigas)
    }

    // Apostas em aberto cuja data é de DIAS_EM_ABERTO dias atrás ou mais (mais antigas primeiro)
    fun apostasAbertasAntigas(apostas: List<Aposta>, agora: Date, dias: Int = DIAS_EM_ABERTO): List<Aposta> {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val hoje = inicioDoDia(agora)
        return apostas.mapNotNull { aposta ->
            if (aposta.lucro != 0.0) return@mapNotNull null
            val data = runCatching { formato.parse(aposta.data) }.getOrNull() ?: return@mapNotNull null
            val diasPassados = TimeUnit.MILLISECONDS.toDays(hoje - inicioDoDia(data))
            if (diasPassados >= dias) aposta to data else null
        }.sortedBy { it.second }.map { it.first }
    }

    private fun inicioDoDia(data: Date): Long = Calendar.getInstance().apply {
        time = data
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun notificar(context: Context, antigas: List<Aposta>) {
        NotificacoesImportacao.criarCanal(context)
        // Tocar abre o app já no filtro "Em Aberto"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_FILTRO, FiltroAposta.EM_ABERTO.name)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendente = PendingIntent.getActivity(
            context, ID_NOTIFICACAO, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = if (antigas.size == 1) "1 aposta em aberto há $DIAS_EM_ABERTO dias ou mais"
        else "${antigas.size} apostas em aberto há $DIAS_EM_ABERTO dias ou mais"
        val estilo = NotificationCompat.InboxStyle()
        antigas.take(5).forEach { estilo.addLine("${it.data} · ${it.descricao.take(60)}") }
        if (antigas.size > 5) estilo.setSummaryText("e mais ${antigas.size - 5}")

        val notificacao = NotificationCompat.Builder(context, NotificacoesImportacao.CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_surebet)
            .setColor(NotificacoesImportacao.COR_TIPS)
            .setContentTitle(titulo)
            .setContentText("Marque Green/Red ou exclua para manter o app em dia.")
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
