// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.recurring

import java.time.LocalDate
import java.time.YearMonth

/**
 * Recurring-payment date and reservation math.
 *
 * Due dates are ISO strings ("2026-09-05") — readable and sortable. MONTHLY items keep an
 * `anchorDay` (1–31): rent anchored to the 31st falls on Feb 28 in February and returns to
 * the 31st in March — the anchor never erodes.
 *
 * Reserved is COMPUTED (never stored): an ACTIVE item reserves its expected amount in the
 * month its nextDue falls in. Matching a real payment advances nextDue, which drops the
 * reservation in the same moment the spend appears — remaining moves by ~₹0 by construction.
 */
object Cadences {
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
}

object RecurringStatus {
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val CANCELLED = "CANCELLED"
}

/** Decoupled from the Room entity; the app maps between them. */
data class RecurringItem(
    val id: Long,
    val name: String,
    val merchantKey: String?,
    val expectedAmountPaise: Long,
    val cadence: String,
    val nextDueIso: String,
    val anchorDay: Int,
    val bucketId: Long?,
    val status: String,
    val createdAt: Long,
)

object RecurringMath {

    /** One cadence step forward, month-length-safe. */
    fun advance(dueIso: String, cadence: String, anchorDay: Int): String {
        val due = LocalDate.parse(dueIso)
        val next = when (cadence) {
            Cadences.WEEKLY -> due.plusDays(7)
            Cadences.MONTHLY -> {
                val month = YearMonth.from(due).plusMonths(1)
                LocalDate.of(month.year, month.month, minOf(anchorDay, month.lengthOfMonth()))
            }
            Cadences.YEARLY -> due.plusYears(1) // java.time clamps Feb 29 -> Feb 28 itself
            else -> throw IllegalArgumentException("unknown cadence: $cadence")
        }
        return next.toString()
    }

    /** Reserved paise per bucketId (null key = unbucketed) for the given month. */
    fun reservedByBucket(items: List<RecurringItem>, month: YearMonth): Map<Long?, Long> =
        items.asSequence()
            .filter { it.status == RecurringStatus.ACTIVE }
            .filter { YearMonth.from(LocalDate.parse(it.nextDueIso)) == month }
            .groupBy({ it.bucketId }, { it.expectedAmountPaise })
            .mapValues { (_, amounts) -> amounts.sum() }

    /** Total reserved for one bucket in a month (convenience over reservedByBucket). */
    fun reservedForBucket(items: List<RecurringItem>, month: YearMonth, bucketId: Long?): Long =
        reservedByBucket(items, month)[bucketId] ?: 0L

    /** ACTIVE items due within [today, today+days], soonest first. */
    fun dueWithin(items: List<RecurringItem>, todayIso: String, days: Long): List<RecurringItem> {
        val today = LocalDate.parse(todayIso)
        val limit = today.plusDays(days)
        return items
            .filter { it.status == RecurringStatus.ACTIVE }
            .filter { val d = LocalDate.parse(it.nextDueIso); !d.isBefore(today) && !d.isAfter(limit) }
            .sortedBy { it.nextDueIso }
    }

    /** "Review this?" age flag: an ACTIVE item untouched for ~6 months. */
    fun isStale(item: RecurringItem, nowMillis: Long): Boolean =
        item.status == RecurringStatus.ACTIVE && nowMillis - item.createdAt > STALE_AFTER_MILLIS

    private const val STALE_AFTER_MILLIS = 183L * 24 * 60 * 60 * 1000 // ~6 months
}
