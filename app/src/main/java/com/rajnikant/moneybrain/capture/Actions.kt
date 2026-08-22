// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

/**
 * The action log: every automatic thing the app does is recorded with enough
 * information to put it back exactly (ARCHITECTURE.md core rule 3).
 *
 * Undo is applied through UndoEngine only — no screen reverses anything by itself.
 */
object ActionKinds {
    const val SMS_CAPTURED = "SMS_CAPTURED"           // target: transaction. Undo: delete it.
    const val AUTO_CATEGORISED = "AUTO_CATEGORISED"   // target: transaction. Undo: restore old category.
    const val RULE_LEARNED = "RULE_LEARNED"           // target: merchant rule. Undo: delete the rule.
    const val ACCOUNT_AUTOCREATED = "ACCOUNT_AUTOCREATED" // target: account. Undo: delete if unused.
    const val SALARY_SPLIT = "SALARY_SPLIT"               // target: salary transaction. Undo: delete its allocations.
    const val RECURRING_MATCHED = "RECURRING_MATCHED"     // target: recurring item. Undo: restore previous nextDue.
    const val RECURRING_SKIPPED = "RECURRING_SKIPPED"     // target: recurring item. Undo: restore previous nextDue.
    const val TRIP_FILED = "TRIP_FILED"                   // target: transaction. Undo: restore previous trip (usually none).
    const val AMOUNT_SPLIT = "AMOUNT_SPLIT"               // target: source transaction (or unallocated pool, id 0). Undo: delete its bucket entries.
    const val BALANCE_CORRECTED = "BALANCE_CORRECTED"     // target: account. Undo: delete the balance snapshot.
}

/** Payload keys. Empty string encodes null. */
object PayloadKeys {
    const val OLD_CATEGORY_ID = "oldCategoryId"
    const val NEW_CATEGORY_ID = "newCategoryId"
    /** Legacy: comma-separated allocation ids from pre-envelope SALARY_SPLIT actions. */
    const val ALLOCATION_IDS = "allocationIds"
    /** Comma-separated bucket entry ids created by an AMOUNT_SPLIT. */
    const val ENTRY_IDS = "entryIds"
    /** Balance snapshot row a BALANCE_CORRECTED action created. */
    const val SNAPSHOT_ID = "snapshotId"
    /** ISO date the recurring item's nextDue held before a match/skip advanced it. */
    const val OLD_NEXT_DUE = "oldNextDue"
    /** Trip id a transaction had before auto-filing ("" = none). */
    const val OLD_TRIP_ID = "oldTripId"
}

/**
 * Tiny line-based map codec for inverse payloads (no JSON library needed, unit-testable).
 * Keys are [A-Za-z]+ only. Values may contain anything; \ and newline are escaped.
 */
object ActionPayload {
    fun encode(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) ->
            require(k.isNotEmpty() && k.all { it.isLetter() }) { "bad payload key: $k" }
            "$k=${escape(v)}"
        }

    fun decode(text: String): Map<String, String> =
        if (text.isBlank()) emptyMap()
        else text.split("\n").associate { line ->
            val i = line.indexOf('=')
            require(i > 0) { "bad payload line" }
            line.substring(0, i) to unescape(line.substring(i + 1))
        }

    private fun escape(v: String): String = buildString {
        for (c in v) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }

    private fun unescape(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    'n' -> { append('\n'); i += 2 }
                    '\\' -> { append('\\'); i += 2 }
                    else -> { append(c); i += 1 }
                }
            } else {
                append(c); i += 1
            }
        }
    }
}

/** A row from the actions table, as UndoEngine needs it. */
data class ActionRecord(
    val id: Long,
    val kind: String,
    val targetType: String,
    val targetId: Long,
    val payload: Map<String, String>,
    val undone: Boolean,
)

/** Storage operations UndoEngine needs. Implemented over Room by the app (RoomUndoStore). */
interface UndoStore {
    suspend fun getAction(id: Long): ActionRecord?
    suspend fun markUndone(id: Long, atMillis: Long)
    suspend fun deleteTransaction(id: Long): Boolean
    /** first = transaction exists, second = its current categoryId (null allowed). */
    suspend fun transactionCategory(id: Long): Pair<Boolean, Long?>
    suspend fun setTransactionCategory(id: Long, categoryId: Long?)
    suspend fun deleteRule(id: Long): Boolean
    suspend fun accountHasTransactions(id: Long): Boolean
    suspend fun deleteAccount(id: Long): Boolean
    /** Deletes the given bucket entries; returns how many rows actually existed. */
    suspend fun deleteBucketEntries(ids: List<Long>): Int
    /** Deletes a balance snapshot; false when it no longer exists. */
    suspend fun deleteBalanceSnapshot(id: Long): Boolean
    /** Restores a recurring item's nextDue; false when the item no longer exists. */
    suspend fun setRecurringNextDue(id: Long, nextDueIso: String): Boolean
    /** Sets a transaction's trip (null = none); false when the transaction no longer exists. */
    suspend fun setTransactionTrip(id: Long, tripId: Long?): Boolean
}

sealed interface UndoResult {
    object Done : UndoResult
    object AlreadyUndone : UndoResult
    /** The thing this action touched no longer exists; the action is marked undone anyway. */
    object TargetGone : UndoResult
    data class Blocked(val reason: String) : UndoResult
}

class UndoEngine(private val store: UndoStore) {

    /** Applies the inverse of one action. Safe to call on stale data; never throws for state reasons. */
    suspend fun undo(actionId: Long, nowMillis: Long): UndoResult {
        val action = store.getAction(actionId) ?: return UndoResult.TargetGone
        if (action.undone) return UndoResult.AlreadyUndone

        val result: UndoResult = when (action.kind) {
            ActionKinds.SMS_CAPTURED ->
                if (store.deleteTransaction(action.targetId)) UndoResult.Done else UndoResult.TargetGone

            ActionKinds.AUTO_CATEGORISED -> {
                val (exists, _) = store.transactionCategory(action.targetId)
                if (!exists) UndoResult.TargetGone
                else {
                    val old = action.payload[PayloadKeys.OLD_CATEGORY_ID].orEmpty()
                    store.setTransactionCategory(action.targetId, old.toLongOrNull())
                    UndoResult.Done
                }
            }

            ActionKinds.RULE_LEARNED ->
                if (store.deleteRule(action.targetId)) UndoResult.Done else UndoResult.TargetGone

            ActionKinds.SALARY_SPLIT, ActionKinds.AMOUNT_SPLIT -> {
                // AMOUNT_SPLIT stores ENTRY_IDS; pre-envelope SALARY_SPLIT rows stored
                // ALLOCATION_IDS — same rows, the v6 migration kept their ids.
                val raw = action.payload[PayloadKeys.ENTRY_IDS]
                    ?: action.payload[PayloadKeys.ALLOCATION_IDS].orEmpty()
                val ids = raw.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (ids.isEmpty()) UndoResult.TargetGone
                else if (store.deleteBucketEntries(ids) > 0) UndoResult.Done
                else UndoResult.TargetGone
            }

            ActionKinds.BALANCE_CORRECTED -> {
                val snapshotId = action.payload[PayloadKeys.SNAPSHOT_ID].orEmpty().toLongOrNull()
                if (snapshotId == null) UndoResult.TargetGone
                else if (store.deleteBalanceSnapshot(snapshotId)) UndoResult.Done
                else UndoResult.TargetGone
            }

            ActionKinds.RECURRING_MATCHED, ActionKinds.RECURRING_SKIPPED -> {
                val oldDue = action.payload[PayloadKeys.OLD_NEXT_DUE]
                if (oldDue.isNullOrBlank()) UndoResult.TargetGone
                else if (store.setRecurringNextDue(action.targetId, oldDue)) UndoResult.Done
                else UndoResult.TargetGone
            }

            ActionKinds.TRIP_FILED -> {
                val oldTrip = action.payload[PayloadKeys.OLD_TRIP_ID].orEmpty().toLongOrNull()
                if (store.setTransactionTrip(action.targetId, oldTrip)) UndoResult.Done
                else UndoResult.TargetGone
            }

            ActionKinds.ACCOUNT_AUTOCREATED ->
                if (store.accountHasTransactions(action.targetId)) {
                    UndoResult.Blocked("This account already has transactions; undo those first.")
                } else if (store.deleteAccount(action.targetId)) {
                    UndoResult.Done
                } else {
                    UndoResult.TargetGone
                }

            else -> UndoResult.Blocked("Unknown action kind: ${action.kind}")
        }

        if (result is UndoResult.Done || result is UndoResult.TargetGone) {
            store.markUndone(actionId, nowMillis)
        }
        return result
    }
}
