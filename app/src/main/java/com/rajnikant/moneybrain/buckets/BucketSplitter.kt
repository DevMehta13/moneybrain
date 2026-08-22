// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.buckets

import com.rajnikant.moneybrain.capture.ActionKinds
import com.rajnikant.moneybrain.capture.PayloadKeys
import com.rajnikant.moneybrain.money.Money

/** Storage operations bucket-money writes need. Implemented over Room by the app (RoomBucketStore). */
interface BucketStore {
    suspend fun insertEntry(
        bucketId: Long,
        amountPaise: Long,
        kind: String,
        sourceTransactionId: Long?,
        note: String?,
        createdAt: Long,
    ): Long

    /**
     * Inserts both legs of a move atomically, each pointing at the other via
     * counterpartEntryId. Returns (fromEntryId, toEntryId).
     */
    suspend fun insertMovePair(
        fromBucketId: Long,
        toBucketId: Long,
        amountPaise: Long,
        createdAt: Long,
    ): Pair<Long, Long>

    /** True when a split already ran for this source transaction (idempotence guard). */
    suspend fun entriesExistForSource(transactionId: Long): Boolean

    suspend fun recordAction(
        kind: String,
        targetType: String,
        targetId: Long,
        description: String,
        payload: Map<String, String>,
        createdAt: Long,
    )
}

sealed interface SplitOutcome {
    data class Done(val entryIds: List<Long>, val leftoverPaise: Long) : SplitOutcome
    object AlreadySplit : SplitOutcome
    /** Every line was zero — nothing written, no action recorded. */
    object NothingToWrite : SplitOutcome
    data class Invalid(val why: SplitValidation) : SplitOutcome
}

/**
 * Executes owner-confirmed bucket-money writes. The owner ALWAYS confirms a split first
 * (decision 2026-08-23) — BucketMath.split only PREFILLS the editor from the template;
 * the lines arriving here are whatever the owner approved.
 *
 * A split writes SPLIT entries plus one AMOUNT_SPLIT action carrying the entry ids, so
 * undo removes exactly what the split created and nothing else. Manual adjust/move write
 * plain entries with no action — they are directly visible and deletable in the bucket's
 * history instead.
 *
 * Call inside a database transaction (the app wraps it in withTransaction).
 */
class BucketSplitter(private val store: BucketStore) {

    /** Split any amount. sourceTransactionId null = splitting unallocated money (no idempotence guard). */
    suspend fun applySplit(
        sourceTransactionId: Long?,
        amountPaise: Long,
        lines: List<SplitLine>,
        nowMillis: Long,
    ): SplitOutcome {
        val validation = BucketLedger.validateSplit(amountPaise, lines)
        if (validation !is SplitValidation.Ok) return SplitOutcome.Invalid(validation)
        if (lines.all { it.amountPaise == 0L }) return SplitOutcome.NothingToWrite
        if (sourceTransactionId != null && store.entriesExistForSource(sourceTransactionId)) {
            return SplitOutcome.AlreadySplit
        }

        val ids = ArrayList<Long>(lines.size)
        for (line in lines) {
            if (line.amountPaise == 0L) continue
            ids.add(
                store.insertEntry(
                    bucketId = line.bucketId,
                    amountPaise = line.amountPaise,
                    kind = EntryKinds.SPLIT,
                    sourceTransactionId = sourceTransactionId,
                    note = null,
                    createdAt = nowMillis,
                ),
            )
        }
        val allocatedTotal = lines.sumOf { it.amountPaise }
        store.recordAction(
            kind = ActionKinds.AMOUNT_SPLIT,
            targetType = if (sourceTransactionId != null) "transaction" else "unallocated",
            targetId = sourceTransactionId ?: 0L,
            description = "Split ${Money.formatPaise(allocatedTotal)} into ${ids.size} " +
                (if (ids.size == 1) "bucket" else "buckets") +
                if (validation.leftoverPaise > 0) {
                    ", ${Money.formatPaise(validation.leftoverPaise)} left unallocated"
                } else {
                    ""
                },
            payload = mapOf(PayloadKeys.ENTRY_IDS to ids.joinToString(",")),
            createdAt = nowMillis,
        )
        return SplitOutcome.Done(ids, validation.leftoverPaise)
    }

    /** Owner adds (+) or takes out (−) money. Signed; zero is meaningless and refused. */
    suspend fun adjust(bucketId: Long, signedAmountPaise: Long, note: String?, nowMillis: Long): Long {
        require(signedAmountPaise != 0L) { "zero adjustment" }
        return store.insertEntry(bucketId, signedAmountPaise, EntryKinds.MANUAL, null, note, nowMillis)
    }

    /** Moves amount between buckets: two MOVE legs that always sum to zero. */
    suspend fun move(fromBucketId: Long, toBucketId: Long, amountPaise: Long, nowMillis: Long): Pair<Long, Long> {
        require(amountPaise > 0) { "move needs a positive amount" }
        require(fromBucketId != toBucketId) { "move needs two different buckets" }
        return store.insertMovePair(fromBucketId, toBucketId, amountPaise, nowMillis)
    }
}
