package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rajnikant.moneybrain.buckets.PlanKinds
import com.rajnikant.moneybrain.buckets.PlanEntry
import com.rajnikant.moneybrain.buckets.BucketSplitter
import com.rajnikant.moneybrain.data.RoomBucketStore
import com.rajnikant.moneybrain.data.BucketEntity
import com.rajnikant.moneybrain.data.BucketPlanEntity
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId

data class BucketStatus(val bucket: BucketEntity, val allocated: Long, val spent: Long)
class BucketsViewModel(private val db: MoneyBrainDatabase) : ViewModel() {
    val buckets = db.bucketDao().observeAll()
    val plans = db.bucketPlanDao().observeAll()
    val status = combine(buckets, db.bucketAllocationDao().observeMonth(YearMonth.now().toString()), db.transactionDao().observeAll(), db.categoryDao().observeAll()) { bs, allocations, txs, categories ->
        bs.map { b -> BucketStatus(b, allocations.filter { it.bucketId == b.id }.sumOf { it.amountPaise }, txs.filter { it.direction == "OUT" && (it.bucketId == b.id || (it.bucketId == null && categories.firstOrNull { c -> c.id == it.categoryId }?.bucketId == b.id)) }.sumOf { it.amountPaise }) }
    }
    val salaryCandidates = combine(db.transactionDao().observeAll(), db.bucketAllocationDao().observeMonth(YearMonth.now().toString())) { txs, allocations ->
        txs.filter { it.direction == "IN" && it.merchant?.contains("salary", true) == true && allocations.none { a -> a.sourceTransactionId == it.id } }
    }
    fun addBucket(name: String) = viewModelScope.launch { if (name.isNotBlank()) db.bucketDao().insert(BucketEntity(name = name.trim(), sortOrder = System.currentTimeMillis().toInt(), createdAt = System.currentTimeMillis())) }
    fun addPercent(bucketId: Long, percent: Long) = viewModelScope.launch { db.bucketPlanDao().insert(BucketPlanEntity(bucketId = bucketId, kind = PlanKinds.PERCENT, value = percent * 100, sortOrder = System.currentTimeMillis().toInt())) }
    fun splitSalary(id: Long, amount: Long, occurredAt: Long) = viewModelScope.launch {
        val month = YearMonth.from(Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault())).toString()
        db.withTransaction { BucketSplitter(RoomBucketStore(db)).splitSalary(id, amount, month, db.bucketPlanDao().observeAll().first().map { PlanEntry(it.bucketId, it.kind, it.value) }, System.currentTimeMillis()) }
    }
}
