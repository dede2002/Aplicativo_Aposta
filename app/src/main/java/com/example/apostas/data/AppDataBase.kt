package com.example.apostas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ao mudar qualquer entidade: suba a versão e adicione uma Migration em addMigrations(...).
// O esquema de cada versão é exportado para app/schemas/ (guarde no git).
@Database(entities = [Aposta::class, DepositoManual::class, Saque::class, LucroTotal::class, NotaEntity::class, LucroDiario::class], version = 8, exportSchema = true)
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

        // 7 → 8: casa onde caiu o resultado de uma surebet com várias casas.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apostas ADD COLUMN casaLucro TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apostas_db"
                )
                    // Sem fallbackToDestructiveMigration: uma versão sem migração falha
                    // em vez de apagar todas as apostas.
                    .addMigrations(MIGRATION_7_8)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}