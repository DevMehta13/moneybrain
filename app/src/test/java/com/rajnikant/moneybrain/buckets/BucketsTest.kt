// ARCHITECT-OWNED — these tests define bucket-money correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.buckets

import com.rajnikant.moneybrain.capture.ActionKinds
import com.rajnikant.moneybrain.capture.ActionRecord
import com.rajnikant.moneybrain.capture.PayloadKeys
import com.rajnikant.moneybrain.capture.UndoEngine
import com.rajnikant.moneybrain.capture.UndoResult
import com.rajnikant.moneybrain.capture.UndoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketMathTest {

    @Test fun `percent and fixed split with truncation leftover`() {
        // Salary ₹80,757.00; 40% savings, ₹25,000 fixed essentials, 20% fun.
        val result = BucketMath.split(
            8_075_700,
            listOf(
                PlanEntry(1, PlanKinds.PERCENT, 4_000),
                PlanEntry(2, PlanKinds.FIXED, 2_500_000),
                PlanEntry(3, PlanKinds.PERCENT, 2_000),
            ),
        )
        assertEquals(3_230_280L, result.lines[0].amountPaise)
        assertEquals(2_500_000L, result.lines[1].amountPaise)
        assertEquals(1_615_140L, result.lines[2].amountPaise)
        assertEquals(730_280L, result.unallocatedPaise)
        // conservation: every paisa accounted for
        assertEquals(8_075_700L, result.lines.sumOf { it.amountPaise } + result.unallocatedPaise)
    }

    @Test fun `hundred percent single bucket takes everything`() {
        val result = BucketMath.split(123_457, listOf(PlanEntry(1, PlanKinds.PERCENT, 10_000)))
        assertEquals(123_457L, result.lines[0].amountPaise)
        assertEquals(0L, result.unallocatedPaise)
    }

    @Test fun `thirds truncate and leftover stays unallocated`() {
        val result = BucketMath.split(
            100,
            listOf(
                PlanEntry(1, PlanKinds.PERCENT, 3_333),
                PlanEntry(2, PlanKinds.PERCENT, 3_333),
                PlanEntry(3, PlanKinds.PERCENT, 3_333),
            ),
        )
        assertEquals(listOf(33L, 33L, 33L), result.lines.map { it.amountPaise })
        assertEquals(1L, result.unallocatedPaise)
    }

    @Test fun `over full plan caps in order and never invents money`() {
        val result = BucketMath.split(
            100_000,
            listOf(
                PlanEntry(1, PlanKinds.FIXED, 80_000),
                PlanEntry(2, PlanKinds.PERCENT, 5_000), // wants 50,000, only 20,000 left
                PlanEntry(3, PlanKinds.FIXED, 10_000),  // nothing left
            ),
        )
        assertEquals(listOf(80_000L, 20_000L, 0L), result.lines.map { it.amountPaise })
        assertEquals(0L, result.unallocatedPaise)
    }

    @Test fun `zero salary splits to zeros`() {
        val result = BucketMath.split(0, listOf(PlanEntry(1, PlanKinds.PERCENT, 5_000)))
        assertEquals(0L, result.lines[0].amountPaise)
        assertEquals(0L, result.unallocatedPaise)
    }

    @Test fun `remaining can go negative to show overspend honestly`() {
        assertEquals(-5_000L, BucketMath.remaining(10_000, 15_000, 0))
        assertEquals(2_000L, BucketMath.remaining(10_000, 5_000, 3_000))
    }

    @Test fun `plan editor totals`() {
        val plan = listOf(
            PlanEntry(1, PlanKinds.PERCENT, 4_000),
            PlanEntry(2, PlanKinds.FIXED, 2_500_000),
            PlanEntry(3, PlanKinds.PERCENT, 2_000),
        )
        assertEquals(6_000L, BucketMath.totalPercentBp(plan))
        assertEquals(2_500_000L, BucketMath.totalFixedPaise(plan))
    }
}

class SalaryDetectorTest {

    @Test fun `neft salary narrative is detected`() {
        assertTrue(
            SalaryDetector.looksLikeSalary(
                "IN",
                "NEFT Cr-CHAS0INBX01-Salary for JUL 2026 ACME SYSTEMS (INDIA) PRIVATE -First Last-CHASH1234567890",
            ),
        )
    }

    @Test fun `ordinary credits and debits are not salary`() {
        assertEquals(false, SalaryDetector.looksLikeSalary("IN", "faasos.payu@indus"))
        assertEquals(false, SalaryDetector.looksLikeSalary("OUT", "salary something"))
        assertEquals(false, SalaryDetector.looksLikeSalary("IN", null))
    }
}

// ---------- splitter + undo ----------

private class FakeBucketStore : BucketStore, UndoStore {
    val allocations = mutableMapOf<Long, Triple<Long, String, Long>>() // id -> (bucketId, month, paise)
    val sources = mutableMapOf<Long, Long>()                           // allocationId -> sourceTransactionId
    val actions = mutableMapOf<Long, ActionRecord>()
    private var nextId = 1L

    override suspend fun insertAllocation(
        bucketId: Long, month: String, amountPaise: Long, sourceTransactionId: Long?, createdAt: Long,
    ): Long {
        val id = nextId++
        allocations[id] = Triple(bucketId, month, amountPaise)
        if (sourceTransactionId != null) sources[id] = sourceTransactionId
        return id
    }

    override suspend fun allocationsExistForSource(transactionId: Long): Boolean =
        sources.containsValue(transactionId)

    override suspend fun recordAction(
        kind: String, targetType: String, targetId: Long,
        description: String, payload: Map<String, String>, createdAt: Long,
    ) {
        val id = nextId++
        actions[id] = ActionRecord(id, kind, targetType, targetId, payload, undone = false)
    }

    // UndoStore (only what SALARY_SPLIT undo touches; the rest must never be called here)
    override suspend fun getAction(id: Long): ActionRecord? = actions[id]
    override suspend fun markUndone(id: Long, atMillis: Long) {
        actions[id] = actions[id]!!.copy(undone = true)
    }
    override suspend fun deleteAllocations(ids: List<Long>): Int =
        ids.count { id -> (allocations.remove(id) != null).also { if (it) sources.remove(id) } }
    override suspend fun deleteTransaction(id: Long) = error("not used")
    override suspend fun transactionCategory(id: Long) = error("not used")
    override suspend fun setTransactionCategory(id: Long, categoryId: Long?) = error("not used")
    override suspend fun deleteRule(id: Long) = error("not used")
    override suspend fun accountHasTransactions(id: Long) = error("not used")
    override suspend fun deleteAccount(id: Long) = error("not used")
}

class BucketSplitterTest {

    private val plan = listOf(
        PlanEntry(1, PlanKinds.PERCENT, 4_000),
        PlanEntry(2, PlanKinds.FIXED, 2_500_000),
    )

    @Test fun `split writes allocations and one undoable action`() = runBlocking {
        val store = FakeBucketStore()
        val outcome = BucketSplitter(store).splitSalary(99, 8_075_700, "2026-08", plan, 1_000L)

        assertTrue(outcome is SplitOutcome.Done)
        assertEquals(2, store.allocations.size)
        val action = store.actions.values.single()
        assertEquals(ActionKinds.SALARY_SPLIT, action.kind)
        assertEquals(99L, action.targetId)
        assertEquals(2, action.payload[PayloadKeys.ALLOCATION_IDS]!!.split(",").size)
    }

    @Test fun `second split of the same salary is refused`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        splitter.splitSalary(99, 8_075_700, "2026-08", plan, 1_000L)
        assertEquals(SplitOutcome.AlreadySplit, splitter.splitSalary(99, 8_075_700, "2026-08", plan, 2_000L))
        assertEquals(2, store.allocations.size)
    }

    @Test fun `empty plan is refused`() = runBlocking {
        val store = FakeBucketStore()
        assertEquals(SplitOutcome.EmptyPlan, BucketSplitter(store).splitSalary(99, 8_075_700, "2026-08", emptyList(), 1_000L))
    }

    @Test fun `zero amount lines are not written`() = runBlocking {
        val store = FakeBucketStore()
        BucketSplitter(store).splitSalary(
            99, 100_000, "2026-08",
            listOf(PlanEntry(1, PlanKinds.FIXED, 100_000), PlanEntry(2, PlanKinds.PERCENT, 5_000)),
            1_000L,
        )
        assertEquals(1, store.allocations.size) // bucket 2 got 0, no row
    }

    @Test fun `undo removes exactly the allocations the split created`() = runBlocking {
        val store = FakeBucketStore()
        BucketSplitter(store).splitSalary(99, 8_075_700, "2026-08", plan, 1_000L)
        store.insertAllocation(3, "2026-08", 50_000, sourceTransactionId = null, createdAt = 1_500L) // unrelated manual allocation
        val actionId = store.actions.entries.single { it.value.kind == ActionKinds.SALARY_SPLIT }.key

        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 2_000L))
        assertEquals(1, store.allocations.size) // only the unrelated one survives
        assertTrue(store.getAction(actionId)!!.undone)
    }

    @Test fun `undo after allocations were already deleted is target gone`() = runBlocking {
        val store = FakeBucketStore()
        BucketSplitter(store).splitSalary(99, 8_075_700, "2026-08", plan, 1_000L)
        store.allocations.clear()
        val actionId = store.actions.entries.single { it.value.kind == ActionKinds.SALARY_SPLIT }.key
        assertEquals(UndoResult.TargetGone, UndoEngine(store).undo(actionId, 2_000L))
    }
}
