package com.example.apostas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Freebet (crédito) ganha numa missão da casa, para extrair depois numa surebet.
// O prejuízo da missão (custo) não entra em saldo nem banca: ele só é descontado do lucro da surebet
// de extração (como o adm do grupo calcula: lucro da extração − o que perdeu para ganhar a freebet).
@Entity(tableName = "freebets")
data class Freebet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Casa ou conta ("Bet365", "Bet365 (Irmã)")
    val casa: String,
    // Valor da freebet
    val valor: Double,
    // Quanto perdeu na missão para ganhar a freebet
    val custo: Double,
    // Quando ganhou (dd/MM/yyyy)
    val data: String,
    // Até quando vale (dd/MM/yyyy); null = sem validade
    val validade: String? = null,
    // Surebet em que foi usada; null = disponível
    val apostaId: Int? = null
) {
    val disponivel: Boolean get() = apostaId == null
}

@Dao
interface FreebetDao {
    @Query("SELECT * FROM freebets ORDER BY id DESC")
    suspend fun getAll(): List<Freebet>

    @Query("SELECT * FROM freebets WHERE apostaId IS NULL ORDER BY id")
    suspend fun disponiveis(): List<Freebet>

    @Insert
    suspend fun inserir(freebet: Freebet)

    @Delete
    suspend fun apagar(freebet: Freebet)

    @Query("UPDATE freebets SET apostaId = :apostaId WHERE id = :id")
    suspend fun usar(id: Int, apostaId: Int)

    // A surebet que usava estas freebets foi excluída (ou a freebet saiu dela na edição): voltam a ficar disponíveis
    @Query("UPDATE freebets SET apostaId = NULL WHERE apostaId = :apostaId")
    suspend fun liberarDaAposta(apostaId: Int)
}

// Dias até a validade (0 = vence hoje, negativo = vencida); null sem validade ou data inválida
fun Freebet.diasParaVencer(agora: Date = Date()): Long? {
    val fim = validade?.let { runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it) }.getOrNull() }
        ?: return null
    return TimeUnit.MILLISECONDS.toDays(inicioDoDia(fim) - inicioDoDia(agora))
}

// Disponíveis que vencem hoje ou amanhã (para o aviso diário)
fun freebetsVencendo(freebets: List<Freebet>, agora: Date = Date()): List<Freebet> =
    freebets.filter { it.disponivel && it.diasParaVencer(agora)?.let { d -> d in 0..1 } == true }

private fun inicioDoDia(data: Date): Long = Calendar.getInstance().apply {
    time = data
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
