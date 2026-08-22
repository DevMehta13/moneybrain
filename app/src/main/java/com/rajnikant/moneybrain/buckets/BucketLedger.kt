// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.buckets

/**
 * The bucket LEDGER (envelope model, owner decision 2026-08-23): a bucket holds a running
 * amount that carries over until spent or moved — months matter only for reports.
 *
 * Money ENTERS via positive ledger entries (splits, manual adds, move-in legs) and LEAVES
 * via negative entries (take-outs, move-out legs) and via spending assigned to the bucket.
 *
 *   balance     = sum of the bucket's entries − everything ever spent from it
 *   available   = balance − reserved for upcoming bills (may go negative — show honestly)
 *   unallocated = total tracked account balance − sum of all bucket balances
 *
 * All computed, never stored (core rule 2).
 */
object EntryKinds {
    const val SPLIT = "SPLIT"   // from splitting a credit or an unallocated amount (always positive)
    const val MANUAL = "MANUAL" // owner add (+) or take-out (−)
    const val MOVE = "MOVE"     // one leg of a bucket-to-bucket move; the two legs always sum to zero
}

/** Pure shape of a bucket_entries row as the math needs it. */
data class LedgerEntry(val id: Long, val bucketId: Long, val amountPaise: Long, val kind: String)

sealed interface SplitValidation {
    /** leftoverPaise simply stays unallocated — a feature, not an error. */
    data class Ok(val leftoverPaise: Long) : SplitValidation
    object NegativeLine : SplitValidation
    data class OverAmount(val overByPaise: Long) : SplitValidation
}

object BucketLedger {

    fun balance(bucketId: Long, entries: List<LedgerEntry>, spentPaise: Long): Long {
        require(spentPaise >= 0) { "negative spent" }
        return entries.filter { it.bucketId == bucketId }.sumOf { it.amountPaise } - spentPaise
    }

    /** Same law as BucketMath.remaining — reserved money is not spendable. */
    fun available(balancePaise: Long, reservedPaise: Long): Long =
        BucketMath.remaining(balancePaise, 0, reservedPaise)

    /** null when the total is unknown (no tracked account). May go negative — show honestly. */
    fun unallocated(totalTrackedPaise: Long?, bucketBalances: List<Long>): Long? =
        totalTrackedPaise?.minus(bucketBalances.sum())

    /** The one validation the split editor and BucketSplitter both obey. */
    fun validateSplit(amountPaise: Long, lines: List<SplitLine>): SplitValidation {
        require(amountPaise >= 0) { "negative amount" }
        if (lines.any { it.amountPaise < 0 }) return SplitValidation.NegativeLine
        val total = lines.sumOf { it.amountPaise }
        if (total > amountPaise) return SplitValidation.OverAmount(total - amountPaise)
        return SplitValidation.Ok(amountPaise - total)
    }
}
