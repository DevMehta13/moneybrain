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
        BucketEntity::class, BucketPlanEntity::class, BucketAllocationEntity::class,
        RecurringEntity::class, RecurringDismissedEntity::class,
        TripEntity::class, PersonEntity::class, PersonLedgerEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class MoneyBrainDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun actionDao(): ActionDao
    abstract fun unparsedSmsDao(): UnparsedSmsDao
    abstract fun bucketDao(): BucketDao
    abstract fun bucketPlanDao(): BucketPlanDao
    abstract fun bucketAllocationDao(): BucketAllocationDao
    abstract fun recurringDao(): RecurringDao
    abstract fun recurringDismissedDao(): RecurringDismissedDao
    abstract fun tripDao(): TripDao
    abstract fun personDao(): PersonDao
    abstract fun personLedgerDao(): PersonLedgerDao

    companion object {
        fun create(context: Context): MoneyBrainDatabase = Room.databaseBuilder(
            context,
            MoneyBrainDatabase::class.java,
            "money-brain.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).addCallback(SeedCallback()).build()
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS buckets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS bucket_plan (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bucketId INTEGER NOT NULL REFERENCES buckets(id) ON DELETE CASCADE, kind TEXT NOT NULL, value INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_bucket_plan_bucketId ON bucket_plan(bucketId)")
    db.execSQL("CREATE TABLE IF NOT EXISTS bucket_allocations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bucketId INTEGER NOT NULL REFERENCES buckets(id) ON DELETE RESTRICT, month TEXT NOT NULL, amountPaise INTEGER NOT NULL, sourceTransactionId INTEGER, createdAt INTEGER NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_bucket_allocations_month_bucketId ON bucket_allocations(month, bucketId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_bucket_allocations_sourceTransactionId ON bucket_allocations(sourceTransactionId)")
    db.execSQL("ALTER TABLE categories ADD COLUMN bucketId INTEGER")
    db.execSQL("ALTER TABLE transactions ADD COLUMN bucketId INTEGER")
} }

private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS recurring (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, merchantKey TEXT, expectedAmountPaise INTEGER NOT NULL, cadence TEXT NOT NULL, nextDue TEXT NOT NULL, anchorDay INTEGER NOT NULL, bucketId INTEGER, status TEXT NOT NULL, createdAt INTEGER NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_status_nextDue ON recurring(status, nextDue)")
    db.execSQL("CREATE TABLE IF NOT EXISTS recurring_dismissed (merchantKey TEXT NOT NULL PRIMARY KEY, dismissedAt INTEGER NOT NULL)")
} }
private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, createdAt INTEGER NOT NULL)"); db.execSQL("CREATE TABLE IF NOT EXISTS people (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)"); db.execSQL("CREATE TABLE IF NOT EXISTS person_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personId INTEGER NOT NULL REFERENCES people(id) ON DELETE RESTRICT, amountPaise INTEGER NOT NULL, kind TEXT NOT NULL, transactionId INTEGER, note TEXT, createdAt INTEGER NOT NULL)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_person_ledger_personId ON person_ledger(personId)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_person_ledger_transactionId ON person_ledger(transactionId)"); db.execSQL("ALTER TABLE transactions ADD COLUMN tripId INTEGER") } }

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
