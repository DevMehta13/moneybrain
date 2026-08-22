package com.rajnikant.moneybrain.data

import androidx.room.withTransaction
import com.rajnikant.moneybrain.capture.ActionPayload
import com.rajnikant.moneybrain.capture.ActionRecord
import com.rajnikant.moneybrain.capture.CaptureStore
import com.rajnikant.moneybrain.capture.NewTransaction
import com.rajnikant.moneybrain.capture.RuleStore
import com.rajnikant.moneybrain.capture.UndoStore
import com.rajnikant.moneybrain.buckets.BucketStore
import com.rajnikant.moneybrain.capture.ActiveTrip
import kotlinx.coroutines.flow.first

class RoomCaptureStore(private val database: MoneyBrainDatabase) : CaptureStore {
    private val accounts = database.accountDao()
    private val rules = database.merchantRuleDao()
    private val transactions = database.transactionDao()
    private val actions = database.actionDao()
    private val unparsed = database.unparsedSmsDao()

    override suspend fun accountIdForBank(bankCode: String): Long? = accounts.idForBankCode(bankCode)

    override suspend fun createAccount(name: String, type: String, bankCode: String, createdAt: Long): Long =
        accounts.insert(AccountEntity(name = name, type = type, createdAt = createdAt, bankCode = bankCode))

    override suspend fun categoryIdForMerchant(merchantKey: String): Long? = rules.categoryIdForMerchant(merchantKey)
    override suspend fun activeTrip(atMillis: Long): ActiveTrip? = database.tripDao().activeAt(atMillis)?.let { ActiveTrip(it.id, it.name) }
    override suspend fun hasActiveRecurringForMerchant(merchantKey: String): Boolean = database.recurringDao().observeAll().first().any { it.merchantKey == merchantKey && it.status == "ACTIVE" }
    override suspend fun fileTransactionToTrip(transactionId: Long, tripId: Long) { transactions.setTrip(transactionId, tripId) }

    override suspend fun insertTransactionIfNew(transaction: NewTransaction): Long? =
        transactions.insertIgnore(
            TransactionEntity(
                amountPaise = transaction.amountPaise,
                direction = transaction.direction,
                accountId = transaction.accountId,
                categoryId = transaction.categoryId,
                merchant = transaction.merchant,
                occurredAt = transaction.occurredAt,
                notes = null,
                source = transaction.source,
                fingerprint = transaction.fingerprint,
                referenceNo = transaction.referenceNo,
                createdAt = transaction.createdAt,
            ),
        ).takeIf { it != -1L }

    override suspend fun recordAction(
        kind: String,
        targetType: String,
        targetId: Long,
        description: String,
        payload: Map<String, String>,
        createdAt: Long,
    ) {
        actions.insert(
            ActionEntity(
                kind = kind,
                targetType = targetType,
                targetId = targetId,
                description = description,
                payload = ActionPayload.encode(payload),
                createdAt = createdAt,
            ),
        )
    }

    override suspend fun recordUnparsed(sender: String, body: String, receivedAt: Long) {
        unparsed.insert(UnparsedSmsEntity(sender = sender, body = body, receivedAt = receivedAt))
    }
}

class RoomRuleStore(private val database: MoneyBrainDatabase) : RuleStore {
    private val rules = database.merchantRuleDao()
    private val actions = database.actionDao()

    override suspend fun upsertRule(merchantKey: String, categoryId: Long, createdAt: Long): Long =
        database.withTransaction {
            val existing = rules.getByMerchantKey(merchantKey)
            if (existing == null) {
                rules.insert(MerchantRuleEntity(merchantKey = merchantKey, categoryId = categoryId, createdAt = createdAt))
            } else {
                rules.update(existing.copy(categoryId = categoryId, createdAt = createdAt))
                existing.id
            }
        }

    override suspend fun recordAction(
        kind: String,
        targetType: String,
        targetId: Long,
        description: String,
        payload: Map<String, String>,
        createdAt: Long,
    ) {
        actions.insert(
            ActionEntity(
                kind = kind,
                targetType = targetType,
                targetId = targetId,
                description = description,
                payload = ActionPayload.encode(payload),
                createdAt = createdAt,
            ),
        )
    }
}

class RoomUndoStore(private val database: MoneyBrainDatabase) : UndoStore {
    private val actions = database.actionDao()
    private val transactions = database.transactionDao()
    private val rules = database.merchantRuleDao()
    private val accounts = database.accountDao()

    override suspend fun getAction(id: Long): ActionRecord? = actions.getById(id)?.let {
        ActionRecord(it.id, it.kind, it.targetType, it.targetId, ActionPayload.decode(it.payload), it.undoneAt != null)
    }

    override suspend fun markUndone(id: Long, atMillis: Long) = actions.markUndone(id, atMillis)
    override suspend fun deleteTransaction(id: Long): Boolean = transactions.deleteById(id) > 0
    override suspend fun transactionCategory(id: Long): Pair<Boolean, Long?> =
        transactions.getById(id)?.let { true to it.categoryId } ?: (false to null)

    override suspend fun setTransactionCategory(id: Long, categoryId: Long?) = transactions.setCategory(id, categoryId)
    override suspend fun deleteRule(id: Long): Boolean = rules.deleteById(id) > 0
    override suspend fun accountHasTransactions(id: Long): Boolean = accounts.hasTransactions(id)
    override suspend fun deleteAccount(id: Long): Boolean = accounts.deleteById(id) > 0
    override suspend fun deleteBucketEntries(ids: List<Long>): Int =
        if (ids.isEmpty()) 0 else database.withTransaction {
            val entries = database.bucketEntryDao()
            entries.deleteIds((ids + entries.counterpartsFor(ids)).distinct())
        }
    override suspend fun deleteBalanceSnapshot(id: Long): Boolean = database.balanceSnapshotDao().deleteById(id) > 0
    override suspend fun setRecurringNextDue(id: Long, nextDueIso: String): Boolean = database.recurringDao().setNextDue(id, nextDueIso) > 0
    override suspend fun setTransactionTrip(id: Long, tripId: Long?): Boolean = transactions.setTrip(id, tripId) > 0
}

class RoomBucketStore(private val database: MoneyBrainDatabase) : BucketStore {
    override suspend fun insertEntry(bucketId: Long, amountPaise: Long, kind: String, sourceTransactionId: Long?, note: String?, createdAt: Long): Long =
        database.bucketEntryDao().insert(BucketEntryEntity(bucketId = bucketId, amountPaise = amountPaise, kind = kind, sourceTransactionId = sourceTransactionId, note = note, createdAt = createdAt))
    override suspend fun insertMovePair(fromBucketId: Long, toBucketId: Long, amountPaise: Long, createdAt: Long): Pair<Long, Long> {
        val entries = database.bucketEntryDao()
        val fromId = entries.insert(BucketEntryEntity(bucketId = fromBucketId, amountPaise = -amountPaise, kind = com.rajnikant.moneybrain.buckets.EntryKinds.MOVE, sourceTransactionId = null, createdAt = createdAt))
        val toId = entries.insert(BucketEntryEntity(bucketId = toBucketId, amountPaise = amountPaise, kind = com.rajnikant.moneybrain.buckets.EntryKinds.MOVE, sourceTransactionId = null, createdAt = createdAt))
        entries.setCounterpart(fromId, toId)
        entries.setCounterpart(toId, fromId)
        return fromId to toId
    }
    override suspend fun entriesExistForSource(transactionId: Long): Boolean = database.bucketEntryDao().existsForSource(transactionId)
    override suspend fun recordAction(kind: String, targetType: String, targetId: Long, description: String, payload: Map<String, String>, createdAt: Long) {
        database.actionDao().insert(ActionEntity(kind = kind, targetType = targetType, targetId = targetId, description = description, payload = ActionPayload.encode(payload), createdAt = createdAt))
    }
}
