package com.rajnikant.moneybrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantRuleEntity::class,
        ActionEntity::class,
        UnparsedSmsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MoneyBrainDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun actionDao(): ActionDao
    abstract fun unparsedSmsDao(): UnparsedSmsDao

    companion object {
        fun create(context: Context): MoneyBrainDatabase = Room.databaseBuilder(
            context,
            MoneyBrainDatabase::class.java,
            "money-brain.db",
        ).addMigrations(MIGRATION_1_2).addCallback(SeedCallback()).build()
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN bankCode TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS merchant_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "merchantKey TEXT NOT NULL, " +
                "categoryId INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT, " +
                "createdAt INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_rules_merchantKey ON merchant_rules(merchantKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rules_categoryId ON merchant_rules(categoryId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS actions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "kind TEXT NOT NULL, targetType TEXT NOT NULL, targetId INTEGER NOT NULL, " +
                "description TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, undoneAt INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_actions_createdAt ON actions(createdAt)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS unparsed_sms (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, " +
                "receivedAt INTEGER NOT NULL, resolvedAt INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_unparsed_sms_receivedAt ON unparsed_sms(receivedAt)")
        db.execSQL(
            "DELETE FROM accounts WHERE name = 'Bank' AND type = 'BANK' AND bankCode IS NULL " +
                "AND id NOT IN (SELECT DISTINCT accountId FROM transactions)",
        )
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO accounts (name, type, createdAt, bankCode) VALUES (?, ?, ?, ?)",
            arrayOf("Cash", "CASH", now, null),
        )
        listOf(
            "Groceries",
            "Food & Dining",
            "Transport",
            "Rent & Bills",
            "Shopping",
            "Entertainment",
            "Health",
            "Personal",
            "Other",
        ).forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories (name, sortOrder) VALUES (?, ?)",
                arrayOf(name, index),
            )
        }
    }
}
