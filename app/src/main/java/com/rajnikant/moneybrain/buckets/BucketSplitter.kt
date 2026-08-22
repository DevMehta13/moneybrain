// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.buckets

import com.rajnikant.moneybrain.capture.ActionKinds
import com.rajnikant.moneybrain.capture.PayloadKeys
import com.rajnikant.moneybrain.money.Money

/** Storage operations the splitter needs. Implemented over Room by the app (RoomBucketStore). */
interface BucketStore {
    suspend fun insertAllocation(
        bucketId: Long,
        month: String,
        amountPaise: Long,
        sourceTransactionId: Long?,
        createdAt: Long,
    ): Long

    /** True when a split already ran for this source transaction (idempotence guard). */
    suspend fun allocationsExistForSource(transactionId: Long): Boolean

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
    data class Done(val result: SplitResult, val allocationIds: List<Long>) : SplitOutcome
    object AlreadySplit : SplitOutcome
    object EmptyPlan : SplitOutcome
}

/**
 * Executes a salary split: BucketMath decides the amounts, this writes the allocations
 * and one SALARY_SPLIT action whose payload carries the allocation ids — so undo can
 * remove exactly what the split created, and nothing else.
 *
 * Call inside a database transaction (the app wraps it in withTransaction).
 */
class BucketSplitter(private val store: BucketStore) {

    suspend fun splitSalary(
        sourceTransactionId: Long,
        salaryPaise: Long,
        month: String,
        plan: List<PlanEntry>,
        nowMillis: Long,
    ): SplitOutcome {
        if (plan.isEmpty()) return SplitOutcome.EmptyPlan
        if (store.allocationsExistForSource(sourceTransactionId)) return SplitOutcome.AlreadySplit

        val result = BucketMath.split(salaryPaise, plan)
        val ids = ArrayList<Long>(result.lines.size)
        for (line in result.lines) {
            if (line.amountPaise == 0L) continue
            ids.add(
                store.insertAllocation(
                    bucketId = line.bucketId,
                    month = month,
                    amountPaise = line.amountPaise,
                    sourceTransactionId = sourceTransactionId,
                    createdAt = nowMillis,
                ),
            )
        }
        val allocatedTotal = result.lines.sumOf { it.amountPaise }
        store.recordAction(
            kind = ActionKinds.SALARY_SPLIT,
            targetType = "transaction",
            targetId = sourceTransactionId,
            description = "Split salary: ${Money.formatPaise(allocatedTotal)} allocated" +
                if (result.unallocatedPaise > 0) {
                    ", ${Money.formatPaise(result.unallocatedPaise)} left unallocated"
                } else {
                    ""
                },
            payload = mapOf(PayloadKeys.ALLOCATION_IDS to ids.joinToString(",")),
            createdAt = nowMillis,
        )
        return SplitOutcome.Done(result, ids)
    }
}
