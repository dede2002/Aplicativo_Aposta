package com.example.apostas.data

import androidx.room.*

@Dao
interface LucroTotalDao {
    @Query("SELECT * FROM lucro_total WHERE id = 1")
    suspend fun get(): LucroTotal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(lucro: LucroTotal)
}
