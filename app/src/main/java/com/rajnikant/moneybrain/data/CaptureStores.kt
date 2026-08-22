package com.rajnikant.moneybrain.data

import androidx.room.withTransaction
import com.rajnikant.moneybrain.capture.ActionPayload
import com.rajnikant.moneybrain.capture.ActionRecord
import com.rajnikant.moneybrain.capture.CaptureStore
import com.rajnikant.moneybrain.capture.NewTransaction
import com.rajnikant.moneybrain.capture.RuleStore
import com.rajnikant.moneybrain.capture.UndoStore
import com.rajnikant.moneybrain.buckets.BucketStore

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
    override suspend fun deleteAllocations(ids: List<Long>): Int =
        if (ids.isEmpty()) 0 else database.bucketAllocationDao().deleteIds(ids)
}

class RoomBucketStore(private val database: MoneyBrainDatabase) : BucketStore {
    override suspend fun insertAllocation(bucketId: Long, month: String, amountPaise: Long, sourceTransactionId: Long?, createdAt: Long): Long =
        database.bucketAllocationDao().insert(BucketAllocationEntity(bucketId = bucketId, month = month, amountPaise = amountPaise, sourceTransactionId = sourceTransactionId, createdAt = createdAt))
    override suspend fun allocationsExistForSource(transactionId: Long): Boolean = database.bucketAllocationDao().existsForSource(transactionId)
    override suspend fun recordAction(kind: String, targetType: String, targetId: Long, description: String, payload: Map<String, String>, createdAt: Long) {
        database.actionDao().insert(ActionEntity(kind = kind, targetType = targetType, targetId = targetId, description = description, payload = ActionPayload.encode(payload), createdAt = createdAt))
    }
}
