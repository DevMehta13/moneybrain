package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rajnikant.moneybrain.buckets.PlanKinds
import com.rajnikant.moneybrain.buckets.PlanEntry
import com.rajnikant.moneybrain.buckets.SplitOutcome
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.buckets.BucketSplitter
import com.rajnikant.moneybrain.buckets.SalaryDetector
import com.rajnikant.moneybrain.data.RoomBucketStore
import com.rajnikant.moneybrain.data.BucketEntity
import com.rajnikant.moneybrain.data.BucketPlanEntity
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.recurring.RecurringMath
import com.rajnikant.moneybrain.recurring.toItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId

data class BucketStatus(val bucket: BucketEntity, val allocated: Long, val spent: Long, val reserved: Long)
sealed interface BucketMessage {
    data class Text(val value: String) : BucketMessage
}

class BucketsViewModel(private val db: MoneyBrainDatabase) : ViewModel() {
    val buckets = db.bucketDao().observeAll()
    val plans = db.bucketPlanDao().observeAll()
    private val currentMonth = YearMonth.now().toString()
    val status = combine(buckets, db.bucketAllocationDao().observeMonth(currentMonth), db.transactionDao().observeAll(), db.categoryDao().observeAll(), db.recurringDao().observeAll()) { bs, allocations, txs, categories, recurring ->
        val month = YearMonth.parse(currentMonth)
        bs.map { b -> BucketStatus(b, allocations.filter { it.bucketId == b.id }.sumOf { it.amountPaise }, txs.filter { it.direction == "OUT" && YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())).toString() == currentMonth && (it.bucketId == b.id || (it.bucketId == null && categories.firstOrNull { c -> c.id == it.categoryId }?.bucketId == b.id)) }.sumOf { it.amountPaise }, RecurringMath.reservedForBucket(recurring.map { it.toItem() }, month, b.id)) }
    }
    val salaryCandidates = combine(db.transactionDao().observeAll(), db.bucketAllocationDao().observeSourceTransactionIds()) { txs, sources ->
        txs.filter { SalaryDetector.looksLikeSalary(it.direction, it.merchant) && YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())).toString() == currentMonth && it.id !in sources }
    }
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
        if (db.bucketDao().hasAllocations(id)) {
            _messages.send(BucketMessage.Text("This bucket has allocations"))
        } else {
            db.bucketDao().deleteById(id)
        }
    }

    fun splitSalary(id: Long, amount: Long, occurredAt: Long) = viewModelScope.launch {
        val month = YearMonth.from(Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault())).toString()
        when (val outcome = db.withTransaction { BucketSplitter(RoomBucketStore(db)).splitSalary(id, amount, month, db.bucketPlanDao().observeAll().first().map { PlanEntry(it.bucketId, it.kind, it.value) }, System.currentTimeMillis()) }) {
            is SplitOutcome.Done -> _messages.send(BucketMessage.Text("${Money.formatPaise(outcome.result.lines.sumOf { it.amountPaise })} allocated, ${Money.formatPaise(outcome.result.unallocatedPaise)} unallocated"))
            SplitOutcome.AlreadySplit -> _messages.send(BucketMessage.Text("This salary was already split"))
            SplitOutcome.EmptyPlan -> _messages.send(BucketMessage.Text("Add a bucket plan before splitting this salary"))
        }
    }
}
