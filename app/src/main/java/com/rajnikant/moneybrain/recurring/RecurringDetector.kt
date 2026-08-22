// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.recurring

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Finds repeating payments in transaction history: same merchant, similar amount,
 * regular gap. Detection only PROPOSES — the user confirms or dismisses in the UI.
 *
 * Heuristic: >= 3 payments to a merchant; the median gap between consecutive payments
 * decides the cadence (6–8 days weekly, 25–35 monthly, 350–380 yearly); at least 3 amounts
 * must lie within 20% of the median amount. Proposed next due = last payment stepped one
 * cadence forward, anchored to the last payment's day of month.
 */
data class Occurrence(val merchantKey: String, val amountPaise: Long, val occurredAtMillis: Long)

data class RecurringCandidate(
    val merchantKey: String,
    val expectedAmountPaise: Long, // median of observed amounts
    val cadence: String,
    val proposedNextDueIso: String,
    val anchorDay: Int,
    val occurrences: Int,
)

object RecurringDetector {

    fun detect(occurrences: List<Occurrence>, zone: ZoneId): List<RecurringCandidate> =
        occurrences
            .groupBy { it.merchantKey }
            .mapNotNull { (key, group) -> candidateFor(key, group, zone) }
            .sortedByDescending { it.occurrences }

    private fun candidateFor(key: String, group: List<Occurrence>, zone: ZoneId): RecurringCandidate? {
        if (group.size < 3) return null
        val dates = group.map { Instant.ofEpochMilli(it.occurredAtMillis).atZone(zone).toLocalDate() }.sorted()
        val gaps = dates.zipWithNext { a, b -> b.toEpochDay() - a.toEpochDay() }
        val cadence = when (median(gaps)) {
            in 6L..8L -> Cadences.WEEKLY
            in 25L..35L -> Cadences.MONTHLY
            in 350L..380L -> Cadences.YEARLY
            else -> return null
        }
        val medianAmount = median(group.map { it.amountPaise })
        val nearMedian = group.count { occ ->
            val tolerance = medianAmount * 20 / 100
            occ.amountPaise in (medianAmount - tolerance)..(medianAmount + tolerance)
        }
        if (nearMedian < 3) return null

        val last = dates.last()
        return RecurringCandidate(
            merchantKey = key,
            expectedAmountPaise = medianAmount,
            cadence = cadence,
            proposedNextDueIso = RecurringMath.advance(last.toString(), cadence, last.dayOfMonth),
            anchorDay = last.dayOfMonth,
            occurrences = group.size,
        )
    }

    /** Deterministic median for Longs: lower middle of the sorted list. */
    private fun median(values: List<Long>): Long = values.sorted()[(values.size - 1) / 2]
}
