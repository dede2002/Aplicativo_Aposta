package com.example.apostas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Aposta::class, DepositoManual::class, Saque::class, LucroTotal::class, NotaEntity::class, LucroDiario::class], version = 7,exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun apostaDao(): ApostaDao
    abstract fun depositoDao(): DepositoDao
    abstract fun saqueDao(): SaqueDao
    abstract fun LucroTotalDao(): LucroTotalDao
    abstract fun notaDao(): NotaDao
    abstract fun LucroDiarioDao(): LucroDiarioDao



    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apostas_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}