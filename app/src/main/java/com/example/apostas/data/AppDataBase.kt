package com.example.apostas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(entities = [Aposta::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun apostaDao(): ApostaDao

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

                    .fallbackToDestructiveMigration() // força reset do banco se versão mudar
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}