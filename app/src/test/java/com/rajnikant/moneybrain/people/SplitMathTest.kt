// ARCHITECT-OWNED — these tests define split/ledger correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitMathTest {

    @Test fun `thousand rupees across three loses no paisa`() {
        val shares = SplitMath.equalShares(100_000, 3)
        assertEquals(listOf(33_334L, 33_333L, 33_333L), shares)
        assertEquals(100_000L, shares.sum())
    }

    @Test fun `every remainder case sums exactly`() {
        for (total in longArrayOf(0, 1, 99, 100, 101, 999, 100_000, 12_345_67)) {
            for (parts in 1..7) {
                val shares = SplitMath.equalShares(total, parts)
                assertEquals("total=$total parts=$parts", total, shares.sum())
                assertEquals(parts, shares.size)
                // shares differ by at most one paisa
                assertTrue(shares.max() - shares.min() <= 1)
            }
        }
    }

    @Test fun `custom shares validate`() {
        assertTrue(SplitMath.validCustomShares(90_000, listOf(30_000, 30_000)))
        assertTrue(SplitMath.validCustomShares(90_000, listOf(90_000))) // they owe the whole thing
        assertFalse(SplitMath.validCustomShares(90_000, listOf(60_000, 40_000))) // exceeds total
        assertFalse(SplitMath.validCustomShares(90_000, listOf(30_000, 0)))      // zero share
        assertFalse(SplitMath.validCustomShares(90_000, emptyList()))
    }

    @Test fun `ledger signs tell the whole story`() {
        // Dinner ₹900 split with Rahul (+300), I lend him ₹2,000 (+2000),
        // he pays for my auto ₹150 (I_OWE, -150), then we settle.
        val entries = listOf(30_000L, 200_000L, -15_000L)
        val balance = SplitMath.balance(entries)
        assertEquals(215_000L, balance) // Rahul owes me ₹2,150
        assertEquals(-215_000L, SplitMath.settlementAmount(balance))
        assertEquals(0L, SplitMath.balance(entries + SplitMath.settlementAmount(balance)))
    }

    @Test fun `negative balance means I owe them`() {
        val balance = SplitMath.balance(listOf(-50_000L))
        assertEquals(-50_000L, balance)
        assertEquals(50_000L, SplitMath.settlementAmount(balance)) // I pay ₹500, balance zeroes
    }
}
