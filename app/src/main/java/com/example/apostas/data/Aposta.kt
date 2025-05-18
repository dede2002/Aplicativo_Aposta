package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apostas")
data class Aposta(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val descricao: String,
    val casa: String,
    val valor: Double,
    val odds: Double,
    val retornoPotencial: Double,
    val lucro: Double
)
