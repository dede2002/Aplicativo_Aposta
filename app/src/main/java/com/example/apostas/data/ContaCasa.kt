package com.example.apostas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

// Conta extra numa casa de aposta (outro CPF: irmã, namorada...). A conta principal é a própria casa
// ("Bet365"); cada conta extra vira um nome próprio, "Bet365 (Irmã)", usado como casa nas pernas da
// surebet. Assim saldo, Ajustar, Sacar e o diálogo de Green funcionam por conta sem regra nova.
@Entity(tableName = "contas")
data class ContaCasa(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val casa: String,
    val apelido: String
) {
    val nome: String get() = nomeDaConta(casa, apelido)
}

@Dao
interface ContaCasaDao {
    @Query("SELECT * FROM contas ORDER BY casa COLLATE NOCASE, apelido COLLATE NOCASE")
    suspend fun getAll(): List<ContaCasa>

    @Insert
    suspend fun inserir(conta: ContaCasa)

    @Delete
    suspend fun apagar(conta: ContaCasa)
}

fun nomeDaConta(casa: String, apelido: String) = "${casa.trim()} (${apelido.trim()})"

private val REGEX_APELIDO = Regex("""^(.+?)\s*\(([^()]+)\)$""")

// "Bet365 (Irmã)" → "Bet365"; "Bet365" → "Bet365"
fun casaDaConta(nome: String): String = REGEX_APELIDO.matchEntire(nome.trim())?.groupValues?.get(1)?.trim() ?: nome.trim()

// Contas que dá para usar nesta casa: a principal primeiro, depois as cadastradas
fun contasDaCasa(casa: String, contas: List<ContaCasa>): List<String> {
    val base = casaDaConta(casa)
    return listOf(base) + contas.filter { it.casa.equals(base, ignoreCase = true) }.map { it.nome }
}
