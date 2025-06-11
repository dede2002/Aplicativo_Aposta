package com.example.apostas.data

import androidx.room.*


@Dao
interface ApostaDao {

    @Query("SELECT * FROM apostas WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Aposta?

    @Query("SELECT * FROM apostas WHERE data = :data")
    suspend fun getByData(data: String): List<Aposta>

    @Query("SELECT * FROM apostas ORDER BY id DESC")
    suspend fun getAll(): List<Aposta>

    @Query("SELECT * FROM apostas ORDER BY id ASC LIMIT 1")
    suspend fun getApostaMaisAntiga(): Aposta?

    @Query("SELECT COUNT(*) FROM apostas")
    suspend fun getTotalApostas(): Int


    @Insert
    suspend fun insert(aposta: Aposta)

    @Delete
    suspend fun delete(aposta: Aposta)

    @Update
    suspend fun update(aposta: Aposta)
}