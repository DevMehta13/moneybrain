// ARCHITECT-OWNED — these tests define recurring-payment correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.recurring

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(
    id: Long = 1,
    merchantKey: String? = "netflix@upi",
    expected: Long = 19_900,
    cadence: String = Cadences.MONTHLY,
    nextDue: String = "2026-09-05",
    anchorDay: Int = 5,
    bucketId: Long? = 10,
    status: String = RecurringStatus.ACTIVE,
    createdAt: Long = 0,
) = RecurringItem(id, "item$id", merchantKey, expected, cadence, nextDue, anchorDay, bucketId, status, createdAt)

class RecurringMathTest {

    @Test fun `monthly anchor survives february`() {
        // Rent anchored to the 31st: Jan 31 -> Feb 28 -> Mar 31. The anchor never erodes.
        val feb = RecurringMath.advance("2026-01-31", Cadences.MONTHLY, 31)
        assertEquals("2026-02-28", feb)
        assertEquals("2026-03-31", RecurringMath.advance(feb, Cadences.MONTHLY, 31))
    }

    @Test fun `leap february honours the anchor`() =
        assertEquals("2028-02-29", RecurringMath.advance("2028-01-30", Cadences.MONTHLY, 30).let {
            // anchor 30 in a 29-day February clamps to the 29th
            it
        })

    @Test fun `weekly advances seven days`() =
        assertEquals("2026-09-01", RecurringMath.advance("2026-08-25", Cadences.WEEKLY, 25))

    @Test fun `yearly clamps leap day`() =
        assertEquals("2029-02-28", RecurringMath.advance("2028-02-29", Cadences.YEARLY, 29))

    @Test fun `reserved counts only active items due in the month`() {
        val items = listOf(
            item(id = 1, nextDue = "2026-09-05", bucketId = 10, expected = 2_500_000), // rent
            item(id = 2, nextDue = "2026-09-15", bucketId = 10, expected = 19_900),    // netflix, same bucket
            item(id = 3, nextDue = "2026-10-05", bucketId = 10),                       // next month: not reserved
            item(id = 4, nextDue = "2026-09-20", bucketId = null, expected = 34_900),  // unbucketed
            item(id = 5, nextDue = "2026-09-09", bucketId = 10, status = RecurringStatus.PAUSED),
            item(id = 6, nextDue = "2026-09-09", bucketId = 10, status = RecurringStatus.CANCELLED),
        )
        val reserved = RecurringMath.reservedByBucket(items, YearMonth.of(2026, 9))
        assertEquals(2_519_900L, reserved[10L])
        assertEquals(34_900L, reserved[null])
        assertEquals(2_519_900L, RecurringMath.reservedForBucket(items, YearMonth.of(2026, 9), 10L))
        assertEquals(0L, RecurringMath.reservedForBucket(items, YearMonth.of(2026, 11), 10L))
    }

    @Test fun `matching a payment moves reservation to spend with zero net change`() {
        // THE invariant: before match, remaining = alloc - spent - reserved.
        // After match (nextDue advanced out of the month, spend recorded), remaining is unchanged.
        val before = listOf(item(id = 1, nextDue = "2026-09-05", bucketId = 10, expected = 19_900))
        val after = before.map { it.copy(nextDueIso = RecurringMath.advance(it.nextDueIso, it.cadence, it.anchorDay)) }
        val month = YearMonth.of(2026, 9)
        val allocated = 100_000L
        val spentBefore = 0L
        val spentAfter = 19_900L
        val remainingBefore = allocated - spentBefore - RecurringMath.reservedForBucket(before, month, 10L)
        val remainingAfter = allocated - spentAfter - RecurringMath.reservedForBucket(after, month, 10L)
        assertEquals(remainingBefore, remainingAfter)
    }

    @Test fun `due within window sorts soonest first and respects bounds`() {
        val items = listOf(
            item(id = 1, nextDue = "2026-09-10"),
            item(id = 2, nextDue = "2026-09-03"),
            item(id = 3, nextDue = "2026-10-20"),                                  // outside 30 days
            item(id = 4, nextDue = "2026-09-01", status = RecurringStatus.PAUSED), // paused
            item(id = 5, nextDue = "2026-08-30"),                                  // already past today
        )
        val due = RecurringMath.dueWithin(items, "2026-09-01", 30)
        assertEquals(listOf(2L, 1L), due.map { it.id })
    }

    @Test fun `stale flag after six months`() {
        val sixMonthsMillis = 184L * 24 * 60 * 60 * 1000
        assertTrue(RecurringMath.isStale(item(createdAt = 0), sixMonthsMillis))
        assertFalse(RecurringMath.isStale(item(createdAt = 0), 30L * 24 * 60 * 60 * 1000))
        assertFalse(RecurringMath.isStale(item(createdAt = 0, status = RecurringStatus.CANCELLED), sixMonthsMillis))
    }
}

class RecurringMatcherTest {

    @Test fun `exact merchant and amount matches`() {
        val match = RecurringMatcher.match("Netflix@UPI ", 19_900, "OUT", "2026-09-04", listOf(item()))
        assertEquals(1L, match?.id)
    }

    @Test fun `price rise within fifteen percent still matches`() {
        assertEquals(1L, RecurringMatcher.match("netflix@upi", 22_900, "OUT", "2026-09-05", listOf(item()))?.id)
    }

    @Test fun `far off amount does not match`() =
        assertNull(RecurringMatcher.match("netflix@upi", 45_000, "OUT", "2026-09-05", listOf(item())))

    @Test fun `fifty rupee floor covers small subscriptions`() {
        // expected ₹100; 15% would be ₹15 but the ₹50 floor applies
        assertTrue(RecurringMatcher.withinTolerance(14_500, 10_000))
        assertFalse(RecurringMatcher.withinTolerance(15_100, 10_000))
    }

    @Test fun `different merchant never matches`() =
        assertNull(RecurringMatcher.match("spotify@upi", 19_900, "OUT", "2026-09-05", listOf(item())))

    @Test fun `credits and paused items never match`() {
        assertNull(RecurringMatcher.match("netflix@upi", 19_900, "IN", "2026-09-05", listOf(item())))
        assertNull(
            RecurringMatcher.match(
                "netflix@upi", 19_900, "OUT", "2026-09-05",
                listOf(item(status = RecurringStatus.PAUSED)),
            ),
        )
    }

    @Test fun `nearest due date wins a tie`() {
        val items = listOf(
            item(id = 1, nextDue = "2026-09-25"),
            item(id = 2, nextDue = "2026-09-06"),
        )
        assertEquals(2L, RecurringMatcher.match("netflix@upi", 19_900, "OUT", "2026-09-05", items)?.id)
    }
}

class RecurringDetectorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun millis(iso: String): Long =
        java.time.LocalDate.parse(iso).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun `monthly pattern with a price rise is detected`() {
        val candidates = RecurringDetector.detect(
            listOf(
                Occurrence("netflix@upi", 19_900, millis("2026-05-05")),
                Occurrence("netflix@upi", 19_900, millis("2026-06-05")),
                Occurrence("netflix@upi", 22_900, millis("2026-07-06")),
            ),
            zone,
        )
        val c = candidates.single()
        assertEquals(Cadences.MONTHLY, c.cadence)
        assertEquals(19_900L, c.expectedAmountPaise)
        assertEquals("2026-08-06", c.proposedNextDueIso)
        assertEquals(6, c.anchorDay)
    }

    @Test fun `weekly pattern is detected`() {
        val candidates = RecurringDetector.detect(
            listOf(
                Occurrence("gym@upi", 50_000, millis("2026-08-01")),
                Occurrence("gym@upi", 50_000, millis("2026-08-08")),
                Occurrence("gym@upi", 50_000, millis("2026-08-15")),
            ),
            zone,
        )
        assertEquals(Cadences.WEEKLY, candidates.single().cadence)
    }

    @Test fun `irregular gaps are not recurring`() =
        assertTrue(
            RecurringDetector.detect(
                listOf(
                    Occurrence("shop@upi", 10_000, millis("2026-05-01")),
                    Occurrence("shop@upi", 10_000, millis("2026-05-04")),
                    Occurrence("shop@upi", 10_000, millis("2026-07-20")),
                ),
                zone,
            ).isEmpty(),
        )

    @Test fun `two occurrences are not enough`() =
        assertTrue(
            RecurringDetector.detect(
                listOf(
                    Occurrence("netflix@upi", 19_900, millis("2026-06-05")),
                    Occurrence("netflix@upi", 19_900, millis("2026-07-05")),
                ),
                zone,
            ).isEmpty(),
        )

    @Test fun `wildly varying amounts are not recurring`() =
        assertTrue(
            RecurringDetector.detect(
                listOf(
                    Occurrence("store@upi", 5_000, millis("2026-05-10")),
                    Occurrence("store@upi", 90_000, millis("2026-06-10")),
                    Occurrence("store@upi", 250_000, millis("2026-07-10")),
                ),
                zone,
            ).isEmpty(),
        )
}
