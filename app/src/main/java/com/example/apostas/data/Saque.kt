package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saques")
data class Saque(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val casa: String,
    val valor: Double
)