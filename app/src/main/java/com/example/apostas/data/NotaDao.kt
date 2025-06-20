package com.example.apostas.data

import androidx.room.*

@Dao
interface NotaDao {

    @Query("SELECT * FROM notas ORDER BY id DESC LIMIT 1")
    suspend fun getUltimaNota(): NotaEntity?

    @Insert
    suspend fun salvar(nota: NotaEntity)

    @Query("DELETE FROM notas")
    suspend fun limparNotas()
}
