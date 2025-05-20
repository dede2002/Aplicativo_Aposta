package com.example.apostas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DepositoDao {
    @Insert
    suspend fun inserir(deposito: DepositoManual)

    @Query("SELECT * FROM depositos")
    suspend fun getAll(): List<DepositoManual>
}
