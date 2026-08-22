// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.recurring

import com.rajnikant.moneybrain.capture.CaptureProcessor
import java.time.LocalDate
import kotlin.math.abs

/**
 * Decides whether a new OUT transaction pays an ACTIVE recurring item.
 *
 * Rules: exact normalised-merchant match; amount within tolerance of the expected amount —
 * max(15% of expected, ₹50) — so ordinary price rises (Netflix ₹199→₹229) still match while
 * unrelated payments to the same merchant do not. Ties (several items, same merchant) go to
 * the item whose nextDue is closest to the payment date.
 *
 * Matching only DECIDES. Applying a match (advance nextDue + action log) is done by the
 * caller inside a database transaction, per the phase work order.
 */
object RecurringMatcher {

    fun match(
        merchant: String?,
        amountPaise: Long,
        direction: String,
        onDateIso: String,
        items: List<RecurringItem>,
    ): RecurringItem? {
        if (direction != "OUT") return null
        val key = CaptureProcessor.merchantKey(merchant) ?: return null
        val onDate = LocalDate.parse(onDateIso)
        return items
            .asSequence()
            .filter { it.status == RecurringStatus.ACTIVE }
            .filter { it.merchantKey == key }
            .filter { withinTolerance(amountPaise, it.expectedAmountPaise) }
            .minByOrNull { abs(LocalDate.parse(it.nextDueIso).toEpochDay() - onDate.toEpochDay()) }
    }

    fun withinTolerance(amountPaise: Long, expectedPaise: Long): Boolean =
        abs(amountPaise - expectedPaise) <= maxOf(expectedPaise * 15 / 100, MIN_TOLERANCE_PAISE)

    private const val MIN_TOLERANCE_PAISE = 5_000L // ₹50 floor, for small subscriptions
}
