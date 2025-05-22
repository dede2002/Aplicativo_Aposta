package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lucro_total")
data class LucroTotal(
    @PrimaryKey val id: Int = 1,
    val valor: Double
)
