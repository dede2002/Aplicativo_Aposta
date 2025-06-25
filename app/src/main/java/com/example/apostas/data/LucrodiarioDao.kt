package com.example.apostas.data

import androidx.room.*

@Dao
interface LucroDiarioDao {
    @Query("SELECT * FROM LucroDiario WHERE id = 1 LIMIT 1")
    fun get(): LucroDiario?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun salvar(lucroDiario: LucroDiario)
}
