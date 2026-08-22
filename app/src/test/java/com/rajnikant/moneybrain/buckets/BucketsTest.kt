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
import org.junit.Assert.fail
import org.junit.Test

class BucketMathTest {

    @Test fun `percent and fixed split with truncation leftover`() {
        // Amount ₹80,757.00; 40% savings, ₹25,000 fixed essentials, 20% fun.
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

    @Test fun `zero amount splits to zeros`() {
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
    data class Entry(
        val bucketId: Long,
        val amountPaise: Long,
        val kind: String,
        val sourceTransactionId: Long?,
        val counterpartEntryId: Long?,
    )

    val entries = mutableMapOf<Long, Entry>()
    val actions = mutableMapOf<Long, ActionRecord>()
    private var nextId = 1L

    override suspend fun insertEntry(
        bucketId: Long, amountPaise: Long, kind: String,
        sourceTransactionId: Long?, note: String?, createdAt: Long,
    ): Long {
        val id = nextId++
        entries[id] = Entry(bucketId, amountPaise, kind, sourceTransactionId, counterpartEntryId = null)
        return id
    }

    override suspend fun insertMovePair(
        fromBucketId: Long, toBucketId: Long, amountPaise: Long, createdAt: Long,
    ): Pair<Long, Long> {
        val fromId = nextId++
        val toId = nextId++
        entries[fromId] = Entry(fromBucketId, -amountPaise, EntryKinds.MOVE, null, counterpartEntryId = toId)
        entries[toId] = Entry(toBucketId, amountPaise, EntryKinds.MOVE, null, counterpartEntryId = fromId)
        return fromId to toId
    }

    override suspend fun entriesExistForSource(transactionId: Long): Boolean =
        entries.values.any { it.sourceTransactionId == transactionId }

    override suspend fun recordAction(
        kind: String, targetType: String, targetId: Long,
        description: String, payload: Map<String, String>, createdAt: Long,
    ) {
        val id = nextId++
        actions[id] = ActionRecord(id, kind, targetType, targetId, payload, undone = false)
    }

    // UndoStore (only what split/balance undo touches; the rest must never be called here)
    override suspend fun getAction(id: Long): ActionRecord? = actions[id]
    override suspend fun markUndone(id: Long, atMillis: Long) {
        actions[id] = actions[id]!!.copy(undone = true)
    }
    override suspend fun deleteBucketEntries(ids: List<Long>): Int =
        ids.count { entries.remove(it) != null }
    override suspend fun deleteBalanceSnapshot(id: Long) = error("not used")
    override suspend fun deleteTransaction(id: Long) = error("not used")
    override suspend fun transactionCategory(id: Long) = error("not used")
    override suspend fun setTransactionCategory(id: Long, categoryId: Long?) = error("not used")
    override suspend fun deleteRule(id: Long) = error("not used")
    override suspend fun setRecurringNextDue(id: Long, nextDueIso: String) = error("not used")
    override suspend fun setTransactionTrip(id: Long, tripId: Long?) = error("not used")
    override suspend fun accountHasTransactions(id: Long) = error("not used")
    override suspend fun deleteAccount(id: Long) = error("not used")

    fun actionOfKind(kind: String) = actions.entries.single { it.value.kind == kind }
}

class BucketSplitterTest {

    // The owner-confirmed lines a template prefill would produce for ₹80,757.00.
    private val confirmedLines = listOf(SplitLine(1, 3_230_280), SplitLine(2, 2_500_000))

    @Test fun `confirmed split writes entries and one undoable action`() = runBlocking {
        val store = FakeBucketStore()
        val outcome = BucketSplitter(store).applySplit(99, 8_075_700, confirmedLines, 1_000L)

        assertTrue(outcome is SplitOutcome.Done)
        outcome as SplitOutcome.Done
        assertEquals(2, outcome.entryIds.size)
        assertEquals(2_345_420L, outcome.leftoverPaise)
        assertTrue(store.entries.values.all { it.kind == EntryKinds.SPLIT && it.sourceTransactionId == 99L })

        val action = store.actionOfKind(ActionKinds.AMOUNT_SPLIT).value
        assertEquals("transaction", action.targetType)
        assertEquals(99L, action.targetId)
        assertEquals(2, action.payload[PayloadKeys.ENTRY_IDS]!!.split(",").size)
    }

    @Test fun `second split of the same credit is refused`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        splitter.applySplit(99, 8_075_700, confirmedLines, 1_000L)
        assertEquals(SplitOutcome.AlreadySplit, splitter.applySplit(99, 8_075_700, confirmedLines, 2_000L))
        assertEquals(2, store.entries.size)
    }

    @Test fun `splitting unallocated money has no source and no idempotence guard`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        assertTrue(splitter.applySplit(null, 100_000, listOf(SplitLine(1, 100_000)), 1_000L) is SplitOutcome.Done)
        assertTrue(splitter.applySplit(null, 50_000, listOf(SplitLine(2, 50_000)), 2_000L) is SplitOutcome.Done)

        val actions = store.actions.values.filter { it.kind == ActionKinds.AMOUNT_SPLIT }
        assertEquals(2, actions.size)
        assertTrue(actions.all { it.targetType == "unallocated" && it.targetId == 0L })
    }

    @Test fun `editor cannot allocate more than the amount`() = runBlocking {
        val store = FakeBucketStore()
        val outcome = BucketSplitter(store)
            .applySplit(99, 100_000, listOf(SplitLine(1, 70_000), SplitLine(2, 35_000)), 1_000L)
        assertEquals(SplitOutcome.Invalid(SplitValidation.OverAmount(5_000)), outcome)
        assertTrue(store.entries.isEmpty())
        assertTrue(store.actions.isEmpty())
    }

    @Test fun `negative lines are refused`() = runBlocking {
        val store = FakeBucketStore()
        val outcome = BucketSplitter(store).applySplit(99, 100_000, listOf(SplitLine(1, -1)), 1_000L)
        assertEquals(SplitOutcome.Invalid(SplitValidation.NegativeLine), outcome)
        assertTrue(store.entries.isEmpty())
    }

    @Test fun `zero lines are skipped and an all-zero split writes nothing`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)

        splitter.applySplit(99, 100_000, listOf(SplitLine(1, 100_000), SplitLine(2, 0)), 1_000L)
        assertEquals(1, store.entries.size) // bucket 2 got 0, no row

        assertEquals(
            SplitOutcome.NothingToWrite,
            splitter.applySplit(null, 100_000, listOf(SplitLine(1, 0), SplitLine(2, 0)), 2_000L),
        )
        assertEquals(1, store.entries.size)
        assertEquals(1, store.actions.size)
    }

    @Test fun `undo removes exactly the entries the split created`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        splitter.applySplit(99, 8_075_700, confirmedLines, 1_000L)
        splitter.adjust(3, 50_000, note = null, nowMillis = 1_500L) // unrelated manual entry
        val actionId = store.actionOfKind(ActionKinds.AMOUNT_SPLIT).key

        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 2_000L))
        assertEquals(1, store.entries.size) // only the unrelated one survives
        assertTrue(store.getAction(actionId)!!.undone)
    }

    @Test fun `legacy salary split actions still undo after the entries migration`() = runBlocking {
        val store = FakeBucketStore()
        // Pre-envelope state: allocation rows became SPLIT entries with the SAME ids (migration
        // rule), and the old action still carries them under the legacy ALLOCATION_IDS key.
        val a = store.insertEntry(1, 3_230_280, EntryKinds.SPLIT, 99, null, 1_000L)
        val b = store.insertEntry(2, 2_500_000, EntryKinds.SPLIT, 99, null, 1_000L)
        store.recordAction(
            ActionKinds.SALARY_SPLIT, "transaction", 99, "Split salary",
            mapOf(PayloadKeys.ALLOCATION_IDS to "$a,$b"), 1_000L,
        )
        val actionId = store.actionOfKind(ActionKinds.SALARY_SPLIT).key

        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 2_000L))
        assertTrue(store.entries.isEmpty())
    }

    @Test fun `undo after the entries were already deleted is target gone`() = runBlocking {
        val store = FakeBucketStore()
        BucketSplitter(store).applySplit(99, 8_075_700, confirmedLines, 1_000L)
        store.entries.clear()
        val actionId = store.actionOfKind(ActionKinds.AMOUNT_SPLIT).key
        assertEquals(UndoResult.TargetGone, UndoEngine(store).undo(actionId, 2_000L))
    }

    @Test fun `manual adjust writes one signed entry and no action`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        val addId = splitter.adjust(1, 25_000, note = "birthday cash", nowMillis = 1_000L)
        val takeId = splitter.adjust(1, -10_000, note = null, nowMillis = 2_000L)

        assertEquals(25_000L, store.entries[addId]!!.amountPaise)
        assertEquals(-10_000L, store.entries[takeId]!!.amountPaise)
        assertTrue(store.entries.values.all { it.kind == EntryKinds.MANUAL })
        assertTrue(store.actions.isEmpty())
    }

    @Test fun `zero manual adjustment is refused`() = runBlocking {
        val store = FakeBucketStore()
        try {
            BucketSplitter(store).adjust(1, 0, null, 1_000L)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(store.entries.isEmpty())
    }

    @Test fun `move writes two linked legs that sum to zero`() = runBlocking {
        val store = FakeBucketStore()
        val (fromId, toId) = BucketSplitter(store).move(1, 2, 30_000, 1_000L)

        val from = store.entries[fromId]!!
        val to = store.entries[toId]!!
        assertEquals(-30_000L, from.amountPaise)
        assertEquals(30_000L, to.amountPaise)
        assertEquals(0L, from.amountPaise + to.amountPaise)
        assertEquals(toId, from.counterpartEntryId)
        assertEquals(fromId, to.counterpartEntryId)
        assertTrue(store.actions.isEmpty())
    }

    @Test fun `move refuses zero amounts and same-bucket moves`() = runBlocking {
        val store = FakeBucketStore()
        val splitter = BucketSplitter(store)
        try {
            splitter.move(1, 2, 0, 1_000L)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            splitter.move(1, 1, 10_000, 1_000L)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(store.entries.isEmpty())
    }
}
