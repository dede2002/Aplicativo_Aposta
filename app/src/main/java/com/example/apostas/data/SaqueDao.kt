package com.example.apostas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SaqueDao {
    @Insert
    suspend fun inserir(saque: Saque)

    @Query("SELECT * FROM saques")
    suspend fun getAll(): List<Saque>
}
