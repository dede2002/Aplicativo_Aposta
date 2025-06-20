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

    @Query("""
    SELECT data, SUM(lucro) AS lucro
    FROM apostas
    WHERE date(substr(data,7,4)||'-'||substr(data,4,2)||'-'||substr(data,1,2)) >=
    CASE
        WHEN :period = '1d' THEN date('now', '-1 day')
        WHEN :period = '1s' THEN date('now', '-7 days')
        WHEN :period = '1m' THEN date('now', '-1 month')
        WHEN :period = '6m' THEN date('now', '-6 months')
        WHEN :period = '1a' THEN date('now', '-1 year')
        ELSE date('now', '-6 months')
    END
    GROUP BY data
    ORDER BY date(substr(data,7,4)||'-'||substr(data,4,2)||'-'||substr(data,1,2))
""")
    fun getLucrosPorPeriodo(period: String): List<LucroPorDia>


}