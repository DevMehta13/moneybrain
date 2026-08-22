package com.rajnikant.moneybrain.summary

import com.rajnikant.moneybrain.buckets.BucketLedger
import com.rajnikant.moneybrain.buckets.LedgerEntry
import com.rajnikant.moneybrain.data.AccountEntity
import com.rajnikant.moneybrain.data.BalanceSnapshotEntity
import com.rajnikant.moneybrain.data.BucketEntryEntity
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
import com.rajnikant.moneybrain.money.BalanceMath
import com.rajnikant.moneybrain.money.BalanceSnapshot
import com.rajnikant.moneybrain.money.BalanceTxn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class BucketStatus(val bucket: BucketEntity, val balancePaise: Long, val spentPaise: Long, val reservedPaise: Long) {
    val availablePaise: Long get() = BucketLedger.available(balancePaise, reservedPaise)
}
data class MoneyMap(val totalPaise: Long?, val accountBalances: Map<Long, Long?>, val untrackedAccountIds: List<Long>, val unallocatedPaise: Long?)

data class PeopleSummary(val owedToYou: Long, val youOwe: Long)
data class ActiveTripSummary(val trip: TripEntity, val total: Long)

fun bucketStatuses(
    buckets: List<BucketEntity>, entries: List<BucketEntryEntity>, transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>, recurring: List<RecurringEntity>, monthText: String,
): List<BucketStatus> {
    val month = YearMonth.parse(monthText)
    val ledger = entries.map { LedgerEntry(it.id, it.bucketId, it.amountPaise, it.kind) }
    return buckets.map { bucket ->
        val spent = transactions.filter { transaction ->
            transaction.direction == "OUT" &&
                (transaction.bucketId == bucket.id || (transaction.bucketId == null && categories.firstOrNull { it.id == transaction.categoryId }?.bucketId == bucket.id))
        }.sumOf { it.amountPaise }
        val reserved = RecurringMath.reservedForBucket(recurring.map { it.toItem() }, month, bucket.id)
        BucketStatus(
            bucket,
            BucketLedger.balance(bucket.id, ledger, spent), spent, reserved,
        )
    }
}

fun moneyMap(accounts: List<AccountEntity>, snapshots: List<BalanceSnapshotEntity>, transactions: List<TransactionEntity>, bucketStatuses: List<BucketStatus>): MoneyMap {
    val balanceSnapshots = snapshots.map { BalanceSnapshot(it.id, it.accountId, it.balancePaise, it.asOfMillis) }
    val balanceTransactions = transactions.map { BalanceTxn(it.accountId, it.amountPaise, it.direction, it.occurredAt) }
    val total = BalanceMath.totalBalance(accounts.map { it.id }, balanceSnapshots, balanceTransactions)
    val balances = accounts.associate { it.id to BalanceMath.accountBalance(it.id, balanceSnapshots, balanceTransactions) }
    return MoneyMap(total.totalPaise, balances, total.untrackedAccountIds, BucketLedger.unallocated(total.totalPaise, bucketStatuses.map { it.balancePaise }))
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
