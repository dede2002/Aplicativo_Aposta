package com.example.apostas.data

import androidx.room.*
import com.example.apostas.data.Aposta

@Dao
interface ApostaDao {

    @Query("SELECT * FROM apostas ORDER BY id DESC")
    suspend fun getAll(): List<Aposta>

    @Insert
    suspend fun insert(aposta: Aposta)

    @Delete
    suspend fun delete(aposta: Aposta)
}
