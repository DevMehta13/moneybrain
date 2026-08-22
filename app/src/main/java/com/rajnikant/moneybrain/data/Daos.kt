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
    @Query("UPDATE transactions SET bucketId = :bucketId WHERE id = :id") suspend fun setBucket(id: Long, bucketId: Long?)
    @Query("UPDATE transactions SET tripId = :tripId WHERE id = :id") suspend fun setTrip(id: Long, tripId: Long?): Int

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?
}
@Dao interface TripDao { @Query("SELECT * FROM trips ORDER BY startedAt DESC") fun observeAll(): Flow<List<TripEntity>>; @Insert suspend fun insert(item: TripEntity): Long; @Query("SELECT * FROM trips WHERE id = :id") suspend fun getById(id: Long): TripEntity?; @Query("UPDATE trips SET endedAt = :at WHERE id = :id") suspend fun stop(id: Long, at: Long): Int; @Query("SELECT * FROM trips WHERE endedAt IS NULL AND startedAt <= :at ORDER BY startedAt DESC LIMIT 1") suspend fun activeAt(at: Long): TripEntity? }
@Dao interface PersonDao { @Query("SELECT * FROM people ORDER BY name") fun observeAll(): Flow<List<PersonEntity>>; @Insert suspend fun insert(item: PersonEntity): Long; @Query("SELECT * FROM people WHERE id = :id") suspend fun getById(id: Long): PersonEntity? }
data class PersonBalance(val personId: Long, val balance: Long)
@Dao interface PersonLedgerDao { @Query("SELECT * FROM person_ledger WHERE personId = :personId ORDER BY createdAt DESC") fun observeForPerson(personId: Long): Flow<List<PersonLedgerEntity>>; @Query("SELECT * FROM person_ledger ORDER BY createdAt DESC") fun observeAll(): Flow<List<PersonLedgerEntity>>; @Query("SELECT * FROM person_ledger WHERE transactionId = :transactionId AND kind = 'SPLIT' ORDER BY id") fun observeSplitsForTransaction(transactionId: Long): Flow<List<PersonLedgerEntity>>; @Insert suspend fun insert(item: PersonLedgerEntity): Long; @Query("DELETE FROM person_ledger WHERE id = :id") suspend fun deleteById(id: Long): Int; @Query("DELETE FROM person_ledger WHERE transactionId = :transactionId AND kind = 'SPLIT'") suspend fun deleteSplitsForTransaction(transactionId: Long): Int; @Query("SELECT personId, COALESCE(SUM(amountPaise), 0) AS balance FROM person_ledger GROUP BY personId") fun observeBalances(): Flow<List<PersonBalance>> }

@Dao interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDue, id") fun observeAll(): Flow<List<RecurringEntity>>
    @Insert suspend fun insert(item: RecurringEntity): Long
    @Update suspend fun update(item: RecurringEntity)
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun getById(id: Long): RecurringEntity?
    @Query("UPDATE recurring SET nextDue = :iso WHERE id = :id") suspend fun setNextDue(id: Long, iso: String): Int
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun deleteById(id: Long): Int
}
@Dao interface RecurringDismissedDao {
    @Query("SELECT * FROM recurring_dismissed") fun observeAll(): Flow<List<RecurringDismissedEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: RecurringDismissedEntity)
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
    @Update suspend fun update(category: CategoryEntity)
}

@Dao interface BucketDao {
    @Query("SELECT * FROM buckets ORDER BY sortOrder, id") fun observeAll(): Flow<List<BucketEntity>>
    @Insert suspend fun insert(bucket: BucketEntity): Long
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM buckets") suspend fun maxSortOrder(): Int
    @Query("DELETE FROM buckets WHERE id = :id") suspend fun deleteById(id: Long): Int
    @Query("SELECT EXISTS(SELECT 1 FROM bucket_allocations WHERE bucketId = :id)") suspend fun hasAllocations(id: Long): Boolean
}
@Dao interface BucketPlanDao {
    @Query("SELECT * FROM bucket_plan ORDER BY sortOrder, id") fun observeAll(): Flow<List<BucketPlanEntity>>
    @Insert suspend fun insert(entry: BucketPlanEntity): Long
    @Update suspend fun update(entry: BucketPlanEntity)
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM bucket_plan") suspend fun maxSortOrder(): Int
    @Query("DELETE FROM bucket_plan WHERE id = :id") suspend fun deleteById(id: Long): Int
    @Query("DELETE FROM bucket_plan WHERE bucketId = :bucketId") suspend fun deleteForBucket(bucketId: Long): Int
}
@Dao interface BucketAllocationDao {
    @Insert suspend fun insert(allocation: BucketAllocationEntity): Long
    @Query("SELECT EXISTS(SELECT 1 FROM bucket_allocations WHERE sourceTransactionId = :id)") suspend fun existsForSource(id: Long): Boolean
    @Query("DELETE FROM bucket_allocations WHERE id IN (:ids)") suspend fun deleteIds(ids: List<Long>): Int
    @Query("SELECT * FROM bucket_allocations WHERE month = :month") fun observeMonth(month: String): Flow<List<BucketAllocationEntity>>
    @Query("SELECT DISTINCT sourceTransactionId FROM bucket_allocations WHERE sourceTransactionId IS NOT NULL") fun observeSourceTransactionIds(): Flow<List<Long>>
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
