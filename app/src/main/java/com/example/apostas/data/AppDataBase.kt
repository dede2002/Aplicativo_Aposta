package com.example.apostas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Ao mudar qualquer entidade: suba a versão e adicione uma Migration em addMigrations(...).
// O esquema de cada versão é exportado para app/schemas/ (guarde no git).
@Database(entities = [Aposta::class, DepositoManual::class, Saque::class, LucroTotal::class, NotaEntity::class, LucroDiario::class], version = 12, exportSchema = true)
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

        // 8 → 9: origem no Telegram das apostas importadas automaticamente (evita duplicar).
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apostas ADD COLUMN origemTelegram TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_apostas_origemTelegram` ON `apostas` (`origemTelegram`)")
            }
        }

        // 9 → 10: o que foi apostado em cada casa (texto + print) nas surebets importadas.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apostas ADD COLUMN pernasJson TEXT")
            }
        }

        // 10 → 11: surebets novas com a stake de cada casa no saldo. As existentes ficam com 0 (regra antiga).
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apostas ADD COLUMN stakesNasCasas INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 11 → 12: depósito automático ligado à surebet (para desfazer ao excluir a surebet em aberto).
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE depositos ADD COLUMN apostaId INTEGER")
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
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}