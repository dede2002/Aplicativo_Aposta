package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "apostas")
data class Aposta(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val descricao: String,
    val casa: String,
    val valor: Double,
    val odds: Double,
    val retornoPotencial: Double,
    val lucro: Double,
    val data: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
)

data class LucroPorDia(
    val data: String,
    val lucro: Double
)

