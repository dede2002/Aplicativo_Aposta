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

    // Desfaz os depósitos automáticos das stakes de uma surebet excluída em aberto
    @Query("DELETE FROM depositos WHERE apostaId = :apostaId")
    suspend fun apagarDaAposta(apostaId: Int)
}
