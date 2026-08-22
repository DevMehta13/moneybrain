package com.rajnikant.moneybrain.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT id FROM accounts WHERE bankCode = :bankCode LIMIT 1")
    suspend fun idForBankCode(bankCode: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE accountId = :id)")
    suspend fun hasTransactions(id: Long): Boolean

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT categoryId FROM merchant_rules WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun categoryIdForMerchant(merchantKey: String): Long?

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getByMerchantKey(merchantKey: String): MerchantRuleEntity?

    @Insert
    suspend fun insert(rule: MerchantRuleEntity): Long

    @Update
    suspend fun update(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface ActionDao {
    @Insert
    suspend fun insert(action: ActionEntity): Long

    @Query("SELECT * FROM actions ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE id = :id")
    suspend fun getById(id: Long): ActionEntity?

    @Query("UPDATE actions SET undoneAt = :atMillis WHERE id = :id")
    suspend fun markUndone(id: Long, atMillis: Long)
}

@Dao
interface UnparsedSmsDao {
    @Insert
    suspend fun insert(message: UnparsedSmsEntity): Long

    @Query("SELECT * FROM unparsed_sms WHERE resolvedAt IS NULL ORDER BY receivedAt DESC, id DESC")
    fun observeUnresolved(): Flow<List<UnparsedSmsEntity>>

    @Query("UPDATE unparsed_sms SET resolvedAt = :atMillis WHERE id = :id")
    suspend fun dismiss(id: Long, atMillis: Long)
}
