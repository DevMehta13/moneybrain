// ARCHITECT-OWNED — these tests define balance-tracking correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BalanceMathTest {

    @Test fun `credits add and debits subtract`() {
        assertEquals(50_000L, BalanceMath.signedPaise("IN", 50_000))
        assertEquals(-50_000L, BalanceMath.signedPaise("OUT", 50_000))
    }

    @Test fun `unknown direction and negative amount are programming errors`() {
        try {
            BalanceMath.signedPaise("SIDEWAYS", 100)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            BalanceMath.signedPaise("IN", -1)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test fun `account without a snapshot is untracked - null, never zero`() {
        assertNull(BalanceMath.accountBalance(1, emptyList(), listOf(BalanceTxn(1, 10_000, "IN", 500))))
    }

    @Test fun `balance is snapshot plus signed transactions strictly after it`() {
        val snapshots = listOf(BalanceSnapshot(id = 1, accountId = 1, balancePaise = 500_000, asOfMillis = 1_000))
        val transactions = listOf(
            BalanceTxn(1, 100_000, "IN", occurredAt = 1_000),  // AT the snapshot instant: already inside the stated number
            BalanceTxn(1, 999_999, "OUT", occurredAt = 900),   // before it: also inside
            BalanceTxn(1, 20_000, "OUT", occurredAt = 1_500),
            BalanceTxn(1, 50_000, "IN", occurredAt = 2_000),
            BalanceTxn(2, 77_777, "IN", occurredAt = 1_500),   // other account: never leaks in
        )
        assertEquals(530_000L, BalanceMath.accountBalance(1, snapshots, transactions))
    }

    @Test fun `newest snapshot wins and same-instant ties break by id`() {
        val snapshots = listOf(
            BalanceSnapshot(1, 1, 100, asOfMillis = 1_000),
            BalanceSnapshot(3, 1, 300, asOfMillis = 2_000), // the correction made an instant after id=2
            BalanceSnapshot(2, 1, 200, asOfMillis = 2_000),
        )
        assertEquals(300L, BalanceMath.accountBalance(1, snapshots, emptyList()))
    }

    @Test fun `total sums tracked accounts and names the untracked ones`() {
        val snapshots = listOf(
            BalanceSnapshot(1, 1, 500_000, 1_000),
            BalanceSnapshot(2, 3, 250_000, 1_000),
        )
        val total = BalanceMath.totalBalance(listOf(1, 2, 3), snapshots, emptyList())
        assertEquals(750_000L, total.totalPaise)
        assertEquals(listOf(1L, 3L), total.trackedAccountIds)
        assertEquals(listOf(2L), total.untrackedAccountIds)
    }

    @Test fun `total is null when nothing is tracked`() {
        val total = BalanceMath.totalBalance(listOf(1, 2), emptyList(), emptyList())
        assertNull(total.totalPaise)
        assertEquals(listOf(1L, 2L), total.untrackedAccountIds)
    }

    @Test fun `correction delta is stated minus computed`() {
        assertEquals(45_678L, BalanceMath.correctionDelta(12_345_678, 12_300_000))  // app was missing money
        assertEquals(-23_000L, BalanceMath.correctionDelta(12_300_000, 12_323_000)) // unseen spending
    }
}
