package com.example.apostas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Aposta::class, DepositoManual::class, Saque::class, LucroTotal::class], version = 5,exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun apostaDao(): ApostaDao
    abstract fun depositoDao(): DepositoDao
    abstract fun saqueDao(): SaqueDao
    abstract fun LucroTotalDao(): LucroTotalDao

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