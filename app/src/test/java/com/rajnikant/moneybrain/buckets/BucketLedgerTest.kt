// ARCHITECT-OWNED — these tests define envelope-bucket correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.buckets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BucketLedgerTest {

    private val entries = listOf(
        LedgerEntry(1, bucketId = 1, amountPaise = 100_000, kind = EntryKinds.SPLIT),
        LedgerEntry(2, bucketId = 1, amountPaise = -30_000, kind = EntryKinds.MANUAL),  // take-out
        LedgerEntry(3, bucketId = 2, amountPaise = 50_000, kind = EntryKinds.SPLIT),
        LedgerEntry(4, bucketId = 1, amountPaise = -10_000, kind = EntryKinds.MOVE),    // moved to bucket 2
        LedgerEntry(5, bucketId = 2, amountPaise = 10_000, kind = EntryKinds.MOVE),
    )

    @Test fun `balance sums only the bucket's own entries minus its all-time spending`() {
        assertEquals(40_000L, BucketLedger.balance(1, entries, spentPaise = 20_000))
        assertEquals(60_000L, BucketLedger.balance(2, entries, spentPaise = 0))
    }

    @Test fun `move legs cancel out across the two buckets`() {
        val before = BucketLedger.balance(1, entries.take(3), 0) + BucketLedger.balance(2, entries.take(3), 0)
        val after = BucketLedger.balance(1, entries, 0) + BucketLedger.balance(2, entries, 0)
        assertEquals(before, after)
    }

    @Test fun `balance goes negative honestly when spending exceeds what the bucket holds`() {
        assertEquals(-25_000L, BucketLedger.balance(2, entries, spentPaise = 85_000))
    }

    @Test fun `available is balance minus reserved and may go negative`() {
        assertEquals(15_000L, BucketLedger.available(40_000, 25_000))
        assertEquals(-5_000L, BucketLedger.available(20_000, 25_000))
    }

    @Test fun `unallocated is total tracked balance minus all bucket balances`() {
        assertEquals(500_000L, BucketLedger.unallocated(1_000_000, listOf(300_000, 200_000)))
        assertEquals(-50_000L, BucketLedger.unallocated(450_000, listOf(300_000, 200_000))) // over-allocated: show it
    }

    @Test fun `unallocated is unknown while no account is tracked`() {
        assertNull(BucketLedger.unallocated(null, listOf(300_000)))
    }

    @Test fun `split validation - exact and partial sums are ok with the leftover reported`() {
        assertEquals(
            SplitValidation.Ok(0),
            BucketLedger.validateSplit(100_000, listOf(SplitLine(1, 60_000), SplitLine(2, 40_000))),
        )
        assertEquals(
            SplitValidation.Ok(25_000),
            BucketLedger.validateSplit(100_000, listOf(SplitLine(1, 75_000))),
        )
    }

    @Test fun `split validation - negative lines and over-allocation are refused`() {
        assertEquals(
            SplitValidation.NegativeLine,
            BucketLedger.validateSplit(100_000, listOf(SplitLine(1, -1))),
        )
        assertEquals(
            SplitValidation.OverAmount(5_000),
            BucketLedger.validateSplit(100_000, listOf(SplitLine(1, 70_000), SplitLine(2, 35_000))),
        )
    }
}
