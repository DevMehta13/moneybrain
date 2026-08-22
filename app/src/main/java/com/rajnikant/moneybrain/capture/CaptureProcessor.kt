// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

import com.rajnikant.moneybrain.money.Money

/**
 * The live-capture pipeline: an incoming SMS becomes (at most) one transaction,
 * with account auto-creation, rule-based categorisation, dedupe by fingerprint,
 * and an action-log trail for everything done automatically.
 *
 * Pure logic against the CaptureStore interface so it is unit-tested on the JVM;
 * the app provides a Room-backed implementation (RoomCaptureStore).
 */
data class NewTransaction(
    val amountPaise: Long,
    val direction: String,
    val accountId: Long,
    val categoryId: Long?,
    val merchant: String?,
    val occurredAt: Long,
    val source: String,
    val fingerprint: String,
    val referenceNo: String?,
    val createdAt: Long,
)

/** The currently running trip, if any (endedAt null, started before the given moment). */
data class ActiveTrip(val id: Long, val name: String)

interface CaptureStore {
    suspend fun accountIdForBank(bankCode: String): Long?
    suspend fun createAccount(name: String, type: String, bankCode: String, createdAt: Long): Long
    /** Exact match on the normalised merchant key; null when no rule exists. */
    suspend fun categoryIdForMerchant(merchantKey: String): Long?
    suspend fun activeTrip(atMillis: Long): ActiveTrip?
    /** True when an ACTIVE recurring item exists for this merchant (bills never file to trips). */
    suspend fun hasActiveRecurringForMerchant(merchantKey: String): Boolean
    suspend fun fileTransactionToTrip(transactionId: Long, tripId: Long)
    /** Inserts unless the fingerprint already exists; returns the new id, or null on duplicate. */
    suspend fun insertTransactionIfNew(transaction: NewTransaction): Long?
    suspend fun recordAction(
        kind: String,
        targetType: String,
        targetId: Long,
        description: String,
        payload: Map<String, String>,
        createdAt: Long,
    )
    suspend fun recordUnparsed(sender: String, body: String, receivedAt: Long)
}

sealed interface CaptureOutcome {
    data class Captured(val transactionId: Long, val categorised: Boolean) : CaptureOutcome
    object Duplicate : CaptureOutcome
    object NeedsAttention : CaptureOutcome
    object Ignored : CaptureOutcome
}

class CaptureProcessor(private val store: CaptureStore) {

    suspend fun process(sender: String, body: String, receivedAtMillis: Long): CaptureOutcome {
        val bank = SmsParser.senderBank(sender) ?: return CaptureOutcome.Ignored
        val parsed = SmsParser.parse(sender, body)
        if (parsed == null) {
            store.recordUnparsed(sender, body, receivedAtMillis)
            return CaptureOutcome.NeedsAttention
        }

        val accountId = store.accountIdForBank(bank) ?: run {
            val name = bankDisplayName(bank)
            val id = store.createAccount(name, "BANK", bank, receivedAtMillis)
            store.recordAction(
                kind = ActionKinds.ACCOUNT_AUTOCREATED,
                targetType = "account",
                targetId = id,
                description = "Added account “$name”",
                payload = emptyMap(),
                createdAt = receivedAtMillis,
            )
            id
        }

        val merchantKey = merchantKey(parsed.merchant)
        val categoryId = merchantKey?.let { store.categoryIdForMerchant(it) }

        val transactionId = store.insertTransactionIfNew(
            NewTransaction(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction,
                accountId = accountId,
                categoryId = categoryId,
                merchant = parsed.merchant,
                occurredAt = receivedAtMillis,
                source = "SMS",
                fingerprint = Fingerprint.of(
                    amountPaise = parsed.amountPaise,
                    direction = parsed.direction,
                    accountHint = parsed.accountHint,
                    occurredAtMillis = receivedAtMillis,
                    referenceNo = parsed.referenceNo,
                ),
                referenceNo = parsed.referenceNo,
                createdAt = receivedAtMillis,
            ),
        ) ?: return CaptureOutcome.Duplicate

        val amountText = Money.formatPaise(parsed.amountPaise)
        val description =
            if (parsed.direction == "IN") {
                "Recorded $amountText received" +
                    (parsed.merchant?.let { " from $it" } ?: "")
            } else {
                "Recorded $amountText paid" +
                    (parsed.merchant?.let { " to $it" } ?: "")
            }
        store.recordAction(
            kind = ActionKinds.SMS_CAPTURED,
            targetType = "transaction",
            targetId = transactionId,
            description = description,
            payload = emptyMap(),
            createdAt = receivedAtMillis,
        )
        if (categoryId != null) {
            store.recordAction(
                kind = ActionKinds.AUTO_CATEGORISED,
                targetType = "transaction",
                targetId = transactionId,
                description = "Auto-categorised ${parsed.merchant.orEmpty()}",
                payload = mapOf(
                    PayloadKeys.OLD_CATEGORY_ID to "",
                    PayloadKeys.NEW_CATEGORY_ID to categoryId.toString(),
                ),
                createdAt = receivedAtMillis,
            )
        }
        if (parsed.direction == "OUT") {
            val trip = store.activeTrip(receivedAtMillis)
            val isBill = merchantKey != null && store.hasActiveRecurringForMerchant(merchantKey)
            if (trip != null && !isBill) {
                store.fileTransactionToTrip(transactionId, trip.id)
                store.recordAction(
                    kind = ActionKinds.TRIP_FILED,
                    targetType = "transaction",
                    targetId = transactionId,
                    description = "Filed to trip “${trip.name}”",
                    payload = mapOf(PayloadKeys.OLD_TRIP_ID to ""),
                    createdAt = receivedAtMillis,
                )
            }
        }
        return CaptureOutcome.Captured(transactionId, categorised = categoryId != null)
    }

    companion object {
        fun bankDisplayName(bankCode: String): String = when (bankCode) {
            "HDFC" -> "HDFC Bank"
            "BOB" -> "Bank of Baroda"
            else -> bankCode
        }

        /** Normalisation shared by rule lookup and rule learning. */
        fun merchantKey(merchant: String?): String? =
            merchant?.trim()?.lowercase()?.ifBlank { null }
    }
}

/** Storage operations rule-learning needs. Implemented over Room by the app. */
interface RuleStore {
    /** Creates or updates the rule for merchantKey; returns the rule id. */
    suspend fun upsertRule(merchantKey: String, categoryId: Long, createdAt: Long): Long
    suspend fun recordAction(
        kind: String,
        targetType: String,
        targetId: Long,
        description: String,
        payload: Map<String, String>,
        createdAt: Long,
    )
}

/**
 * "Corrections become rules": call when the user sets a category on a transaction
 * that has a merchant. Future captures of the same merchant use the rule.
 */
class RuleLearner(private val store: RuleStore) {

    suspend fun learn(merchant: String?, categoryId: Long, categoryName: String, nowMillis: Long): Long? {
        val key = CaptureProcessor.merchantKey(merchant) ?: return null
        val ruleId = store.upsertRule(key, categoryId, nowMillis)
        store.recordAction(
            kind = ActionKinds.RULE_LEARNED,
            targetType = "rule",
            targetId = ruleId,
            description = "Learned: $key → $categoryName",
            payload = emptyMap(),
            createdAt = nowMillis,
        )
        return ruleId
    }
}
