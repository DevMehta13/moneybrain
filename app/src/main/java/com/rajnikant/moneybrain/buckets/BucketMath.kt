// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.buckets

/**
 * Bucket money math. Everything is integer paise; percentages are basis points
 * (Long, 10000 = 100%) so no floating point ever touches money.
 *
 * Split policy (deterministic, user-predictable):
 * - Plan entries apply IN ORDER.
 * - FIXED takes its paise value; PERCENT takes salary * bp / 10000 (integer division).
 * - Each entry is capped by what is still unallocated, so an over-full plan degrades
 *   gracefully instead of inventing money.
 * - Whatever is left after all entries stays UNALLOCATED (a visible feature, not an error;
 *   truncation from integer division also lands there — never more than a few paise).
 */
object PlanKinds {
    const val FIXED = "FIXED"     // value = paise
    const val PERCENT = "PERCENT" // value = basis points (10000 = 100%)
}

data class PlanEntry(val bucketId: Long, val kind: String, val value: Long)
data class SplitLine(val bucketId: Long, val amountPaise: Long)
data class SplitResult(val lines: List<SplitLine>, val unallocatedPaise: Long)

object BucketMath {

    fun split(salaryPaise: Long, plan: List<PlanEntry>): SplitResult {
        require(salaryPaise >= 0) { "negative salary" }
        var remaining = salaryPaise
        val lines = ArrayList<SplitLine>(plan.size)
        for (entry in plan) {
            require(entry.value >= 0) { "negative plan value" }
            val wanted = when (entry.kind) {
                PlanKinds.FIXED -> entry.value
                PlanKinds.PERCENT -> salaryPaise * entry.value / 10_000
                else -> throw IllegalArgumentException("unknown plan kind: ${entry.kind}")
            }
            val take = if (wanted < remaining) wanted else remaining
            lines.add(SplitLine(entry.bucketId, take))
            remaining -= take
        }
        return SplitResult(lines, remaining)
    }

    /** The One Number You Can Trust. May go negative — that means overspent, show it honestly. */
    fun remaining(allocatedPaise: Long, spentPaise: Long, reservedPaise: Long): Long =
        allocatedPaise - spentPaise - reservedPaise

    /** For the plan editor's sanity warning: total percent points in a plan. */
    fun totalPercentBp(plan: List<PlanEntry>): Long =
        plan.filter { it.kind == PlanKinds.PERCENT }.sumOf { it.value }

    /** For the plan editor's sanity warning: total fixed paise in a plan. */
    fun totalFixedPaise(plan: List<PlanEntry>): Long =
        plan.filter { it.kind == PlanKinds.FIXED }.sumOf { it.value }
}

/**
 * Deterministic salary recognition, built on the observed HDFC NEFT narrative which
 * literally contains "Salary" (see BankTemplates hdfc-neft-credit-1). The UI always
 * CONFIRMS with the user before splitting — this only decides what to offer.
 */
object SalaryDetector {
    fun looksLikeSalary(direction: String, merchant: String?): Boolean =
        direction == "IN" && merchant?.contains("salary", ignoreCase = true) == true
}
