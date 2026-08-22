package com.rajnikant.moneybrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MoneyBrainDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun create(context: Context): MoneyBrainDatabase = Room.databaseBuilder(
            context,
            MoneyBrainDatabase::class.java,
            "money-brain.db",
        ).addCallback(SeedCallback()).build()
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO accounts (name, type, createdAt) VALUES (?, ?, ?)",
            arrayOf("Bank", "BANK", now),
        )
        db.execSQL(
            "INSERT INTO accounts (name, type, createdAt) VALUES (?, ?, ?)",
            arrayOf("Cash", "CASH", now),
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
