package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class LucroDiario(
    @PrimaryKey val id: Int = 1,
    val valor: Double
)