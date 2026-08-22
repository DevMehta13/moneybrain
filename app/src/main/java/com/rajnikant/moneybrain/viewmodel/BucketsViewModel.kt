package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rajnikant.moneybrain.buckets.PlanKinds
import com.rajnikant.moneybrain.buckets.PlanEntry
import com.rajnikant.moneybrain.buckets.SplitOutcome
import com.rajnikant.moneybrain.buckets.SplitLine
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.buckets.BucketSplitter
import com.rajnikant.moneybrain.data.RoomBucketStore
import com.rajnikant.moneybrain.data.BucketEntity
import com.rajnikant.moneybrain.data.BucketPlanEntity
import com.rajnikant.moneybrain.data.SplitDismissedEntity
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.summary.BucketStatus
import com.rajnikant.moneybrain.summary.bucketStatuses
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

sealed interface BucketMessage {
    data class Text(val value: String) : BucketMessage
}

class BucketsViewModel(private val db: MoneyBrainDatabase) : ViewModel() {
    val buckets = db.bucketDao().observeAll()
    val plans = db.bucketPlanDao().observeAll()
    val entries = db.bucketEntryDao().observeAll()
    val transactions = db.transactionDao().observeAll()
    val categories = db.categoryDao().observeAll()
    val recurring = db.recurringDao().observeAll()
    val accounts = db.accountDao().observeAll()
    val snapshots = db.balanceSnapshotDao().observeAll()
    val dismissedSplits = db.splitDismissedDao().observeAll()
    val status = combine(buckets, entries, db.transactionDao().observeAll(), db.categoryDao().observeAll(), db.recurringDao().observeAll()) { bs, ledger, txs, categories, recurring -> bucketStatuses(bs, ledger, txs, categories, recurring, java.time.YearMonth.now().toString()) }
    private val _messages = Channel<BucketMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun addBucket(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) {
            db.bucketDao().insert(BucketEntity(name = name.trim(), sortOrder = db.bucketDao().maxSortOrder() + 1, createdAt = System.currentTimeMillis()))
        }
    }

    fun addPercent(bucketId: Long, percent: Long) = addPlan(bucketId, PlanKinds.PERCENT, percent * 100)

    fun addFixed(bucketId: Long, text: String) {
        Money.parseToPaise(text)?.let { addPlan(bucketId, PlanKinds.FIXED, it) }
    }

    private fun addPlan(bucketId: Long, kind: String, value: Long) = viewModelScope.launch {
        db.bucketPlanDao().insert(BucketPlanEntity(bucketId = bucketId, kind = kind, value = value, sortOrder = db.bucketPlanDao().maxSortOrder() + 1))
    }

    fun deletePlan(id: Long) = viewModelScope.launch { db.bucketPlanDao().deleteById(id) }

    fun movePlan(plan: List<BucketPlanEntity>, id: Long, direction: Int) = viewModelScope.launch {
        val index = plan.indexOfFirst { it.id == id }
        val otherIndex = index + direction
        if (index >= 0 && otherIndex in plan.indices) {
            val entry = plan[index]
            val other = plan[otherIndex]
            db.withTransaction {
                db.bucketPlanDao().update(entry.copy(sortOrder = other.sortOrder))
                db.bucketPlanDao().update(other.copy(sortOrder = entry.sortOrder))
            }
        }
    }

    fun deleteBucket(id: Long) = viewModelScope.launch {
        if (db.bucketDao().hasEntries(id)) {
            _messages.send(BucketMessage.Text("Move its money out first."))
        } else {
            db.bucketDao().deleteById(id)
        }
    }

    fun applySplit(sourceTransactionId: Long?, amount: Long, lines: List<SplitLine>) = viewModelScope.launch {
        when (val outcome = db.withTransaction { BucketSplitter(RoomBucketStore(db)).applySplit(sourceTransactionId, amount, lines, System.currentTimeMillis()) }) {
            is SplitOutcome.Done -> _messages.send(BucketMessage.Text("${Money.formatPaise(amount - outcome.leftoverPaise)} allocated, ${Money.formatPaise(outcome.leftoverPaise)} left unallocated"))
            SplitOutcome.AlreadySplit -> _messages.send(BucketMessage.Text("Already split"))
            SplitOutcome.NothingToWrite -> _messages.send(BucketMessage.Text("Nothing to split"))
            is SplitOutcome.Invalid -> _messages.send(BucketMessage.Text("Split is invalid"))
        }
    }
    fun adjust(bucketId: Long, signedAmount: Long, note: String?) = viewModelScope.launch { db.withTransaction { BucketSplitter(RoomBucketStore(db)).adjust(bucketId, signedAmount, note, System.currentTimeMillis()) } }
    fun move(fromBucketId: Long, toBucketId: Long, amount: Long) = viewModelScope.launch { db.withTransaction { BucketSplitter(RoomBucketStore(db)).move(fromBucketId, toBucketId, amount, System.currentTimeMillis()) } }
    fun deleteEntry(id: Long) = viewModelScope.launch { db.withTransaction { val dao = db.bucketEntryDao(); dao.deleteIds((listOf(id) + dao.counterpartsFor(listOf(id))).distinct()) } }
    fun dismissSplit(transactionId: Long) = viewModelScope.launch {
        db.splitDismissedDao().insert(SplitDismissedEntity(transactionId, System.currentTimeMillis()))
    }
}
