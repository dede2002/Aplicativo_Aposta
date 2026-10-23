package com.example.apostas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "depositos")
data class DepositoManual(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val casa: String,
    val valor: Double,
    // Depósito automático da stake de uma surebet nova (v12): id da aposta. Ao excluir a surebet ainda em aberto,
    // esses depósitos são desfeitos. Null nos depósitos manuais e nos automáticos de antes.
    val apostaId: Int? = null
)
