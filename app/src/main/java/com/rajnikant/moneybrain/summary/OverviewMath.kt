package com.rajnikant.moneybrain.summary

import com.rajnikant.moneybrain.buckets.BucketMath
import com.rajnikant.moneybrain.data.BucketAllocationEntity
import com.rajnikant.moneybrain.data.BucketEntity
import com.rajnikant.moneybrain.data.CategoryEntity
import com.rajnikant.moneybrain.data.PersonBalance
import com.rajnikant.moneybrain.data.RecurringEntity
import com.rajnikant.moneybrain.data.RecurringDismissedEntity
import com.rajnikant.moneybrain.data.TransactionEntity
import com.rajnikant.moneybrain.data.TripEntity
import com.rajnikant.moneybrain.recurring.RecurringMath
import com.rajnikant.moneybrain.recurring.RecurringDetector
import com.rajnikant.moneybrain.recurring.RecurringStatus
import com.rajnikant.moneybrain.recurring.Occurrence
import com.rajnikant.moneybrain.recurring.toItem
import com.rajnikant.moneybrain.capture.CaptureProcessor
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class BucketStatus(val bucket: BucketEntity, val allocated: Long, val spent: Long, val reserved: Long) {
    val remaining: Long get() = BucketMath.remaining(allocated, spent, reserved)
}

data class PeopleSummary(val owedToYou: Long, val youOwe: Long)
data class ActiveTripSummary(val trip: TripEntity, val total: Long)

fun bucketStatuses(
    buckets: List<BucketEntity>, allocations: List<BucketAllocationEntity>, transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>, recurring: List<RecurringEntity>, monthText: String,
): List<BucketStatus> {
    val month = YearMonth.parse(monthText)
    return buckets.map { bucket ->
        BucketStatus(
            bucket,
            allocations.filter { it.bucketId == bucket.id }.sumOf { it.amountPaise },
            transactions.filter { transaction ->
                transaction.direction == "OUT" &&
                    YearMonth.from(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault())).toString() == monthText &&
                    (transaction.bucketId == bucket.id || (transaction.bucketId == null && categories.firstOrNull { it.id == transaction.categoryId }?.bucketId == bucket.id))
            }.sumOf { it.amountPaise },
            RecurringMath.reservedForBucket(recurring.map { it.toItem() }, month, bucket.id),
        )
    }
}

fun peopleSummary(balances: List<PersonBalance>): PeopleSummary = PeopleSummary(
    owedToYou = balances.filter { it.balance > 0 }.sumOf { it.balance },
    youOwe = -balances.filter { it.balance < 0 }.sumOf { it.balance },
)

fun upcomingRecurring(items: List<RecurringEntity>, todayIso: String, days: Long = 30) =
    RecurringMath.dueWithin(items.map { it.toItem() }, todayIso, days)

fun detectedRecurring(items: List<RecurringEntity>, transactions: List<TransactionEntity>, dismissed: List<RecurringDismissedEntity>, zoneId: ZoneId) =
    RecurringDetector.detect(transactions.filter { it.direction == "OUT" && it.merchant != null && it.occurredAt >= System.currentTimeMillis() - 183L * 24 * 60 * 60 * 1000 }.mapNotNull { tx -> CaptureProcessor.merchantKey(tx.merchant)?.let { Occurrence(it, tx.amountPaise, tx.occurredAt) } }, zoneId).filter { candidate -> items.none { it.merchantKey == candidate.merchantKey && it.status != RecurringStatus.CANCELLED } && dismissed.none { it.merchantKey == candidate.merchantKey } }

fun tripTotal(transactions: List<TransactionEntity>, tripId: Long): Long =
    transactions.filter { it.tripId == tripId && it.direction == "OUT" }.sumOf { it.amountPaise }

fun activeTripSummary(trips: List<TripEntity>, transactions: List<TransactionEntity>): ActiveTripSummary? =
    trips.firstOrNull { it.endedAt == null }?.let { trip ->
        ActiveTripSummary(trip, tripTotal(transactions, trip.id))
    }
