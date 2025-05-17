package com.example.apostas.data
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StatusAposta {
    ABERTA,
    GANHA,
    PERDIDA
}

@Entity(tableName = "apostas")

data class Aposta(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val descricao: String,         // Qual é a aposta
    val casa: String,              // Casa de aposta
    val valor: Double,             // Valor apostado
    val odds: Double,              // Odds
    val status: StatusAposta,      // Status (GANHA, PERDIDA, ABERTA)
    val retornoPotencial: Double,  // valor * odds
    val lucro: Double              // retorno real - valor (se finalizada)
)

