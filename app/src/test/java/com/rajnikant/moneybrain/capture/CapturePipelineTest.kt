// ARCHITECT-OWNED — these tests define capture/undo correctness; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.capture

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------- in-memory fakes ----------

private class FakeStore : CaptureStore, UndoStore, RuleStore {
    val accounts = mutableMapOf<Long, Triple<String, String, String>>() // id -> (name, type, bankCode)
    val rules = mutableMapOf<String, Pair<Long, Long>>()                // key -> (ruleId, categoryId)
    val transactions = mutableMapOf<Long, NewTransaction>()
    val actions = mutableMapOf<Long, ActionRecord>()
    val actionDescriptions = mutableListOf<String>()
    val unparsed = mutableListOf<Pair<String, String>>()
    private var nextId = 1L
    private fun id() = nextId++

    // CaptureStore
    override suspend fun accountIdForBank(bankCode: String): Long? =
        accounts.entries.firstOrNull { it.value.third == bankCode }?.key

    override suspend fun createAccount(name: String, type: String, bankCode: String, createdAt: Long): Long =
        id().also { accounts[it] = Triple(name, type, bankCode) }

    override suspend fun categoryIdForMerchant(merchantKey: String): Long? = rules[merchantKey]?.second

    override suspend fun insertTransactionIfNew(transaction: NewTransaction): Long? {
        if (transactions.values.any { it.fingerprint == transaction.fingerprint }) return null
        return id().also { transactions[it] = transaction }
    }

    override suspend fun recordAction(
        kind: String, targetType: String, targetId: Long,
        description: String, payload: Map<String, String>, createdAt: Long,
    ) {
        val actionId = id()
        actions[actionId] = ActionRecord(actionId, kind, targetType, targetId, payload, undone = false)
        actionDescriptions.add(description)
    }

    override suspend fun recordUnparsed(sender: String, body: String, receivedAt: Long) {
        unparsed.add(sender to body)
    }

    // RuleStore
    override suspend fun upsertRule(merchantKey: String, categoryId: Long, createdAt: Long): Long {
        val existing = rules[merchantKey]
        val ruleId = existing?.first ?: id()
        rules[merchantKey] = ruleId to categoryId
        return ruleId
    }

    // UndoStore
    override suspend fun getAction(id: Long): ActionRecord? = actions[id]
    override suspend fun markUndone(id: Long, atMillis: Long) {
        actions[id] = actions[id]!!.copy(undone = true)
    }
    override suspend fun deleteTransaction(id: Long): Boolean = transactions.remove(id) != null
    override suspend fun transactionCategory(id: Long): Pair<Boolean, Long?> =
        (transactions[id] != null) to transactions[id]?.categoryId
    override suspend fun setTransactionCategory(id: Long, categoryId: Long?) {
        transactions[id] = transactions[id]!!.copy(categoryId = categoryId)
    }
    override suspend fun deleteRule(id: Long): Boolean {
        val key = rules.entries.firstOrNull { it.value.first == id }?.key ?: return false
        rules.remove(key); return true
    }
    override suspend fun accountHasTransactions(id: Long): Boolean =
        transactions.values.any { it.accountId == id }
    override suspend fun deleteAccount(id: Long): Boolean = accounts.remove(id) != null

    fun actionsOfKind(kind: String) = actions.values.filter { it.kind == kind }
}

private val BOB_DEBIT = "Rs.60.00 Dr. from A/C 1234567890 and Cr. to crazzyproduct@axl. " +
    "Ref:858963521470. AvlBal:Rs1112.11(2026:08:21 01:34:51). Not you? Call 18002584455/8468001111-BOB"

// ---------- capture ----------

class CaptureProcessorTest {

    @Test fun `capture creates account transaction and action log`() = runBlocking {
        val store = FakeStore()
        val outcome = CaptureProcessor(store).process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)

        assertTrue(outcome is CaptureOutcome.Captured)
        assertEquals(1, store.transactions.size)
        assertEquals("Bank of Baroda", store.accounts.values.single().first)
        assertEquals(1, store.actionsOfKind(ActionKinds.ACCOUNT_AUTOCREATED).size)
        assertEquals(1, store.actionsOfKind(ActionKinds.SMS_CAPTURED).size)
        // no rule yet -> no auto-categorise action, transaction uncategorised
        assertEquals(0, store.actionsOfKind(ActionKinds.AUTO_CATEGORISED).size)
        assertNull(store.transactions.values.single().categoryId)
    }

    @Test fun `same payment twice is a duplicate`() = runBlocking {
        val store = FakeStore()
        val processor = CaptureProcessor(store)
        processor.process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)
        val second = processor.process("JK-BOBSMS-S", BOB_DEBIT, 1_030_000L)
        assertEquals(CaptureOutcome.Duplicate, second)
        assertEquals(1, store.transactions.size)
    }

    @Test fun `account is created only once`() = runBlocking {
        val store = FakeStore()
        val processor = CaptureProcessor(store)
        processor.process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)
        processor.process("JK-BOBSMS-S", BOB_DEBIT.replace("Rs.60.00", "Rs.75.00"), 2_000_000L)
        assertEquals(1, store.accounts.size)
    }

    @Test fun `rule applies and logs auto categorisation`() = runBlocking {
        val store = FakeStore()
        store.upsertRule("crazzyproduct@axl", categoryId = 42, createdAt = 0)
        val outcome = CaptureProcessor(store).process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)

        assertEquals(true, (outcome as CaptureOutcome.Captured).categorised)
        assertEquals(42L, store.transactions.values.single().categoryId)
        val auto = store.actionsOfKind(ActionKinds.AUTO_CATEGORISED).single()
        assertEquals("42", auto.payload[PayloadKeys.NEW_CATEGORY_ID])
        assertEquals("", auto.payload[PayloadKeys.OLD_CATEGORY_ID])
    }

    @Test fun `unparsed bank sms goes to needs attention`() = runBlocking {
        val store = FakeStore()
        val outcome = CaptureProcessor(store).process("JK-BOBSMS-S", "Your OTP is 4821", 1_000_000L)
        assertEquals(CaptureOutcome.NeedsAttention, outcome)
        assertEquals(1, store.unparsed.size)
        assertEquals(0, store.transactions.size)
    }

    @Test fun `non bank sender is ignored entirely`() = runBlocking {
        val store = FakeStore()
        val outcome = CaptureProcessor(store).process("AX-ZOMATO", BOB_DEBIT, 1_000_000L)
        assertEquals(CaptureOutcome.Ignored, outcome)
        assertEquals(0, store.unparsed.size)
        assertEquals(0, store.transactions.size)
    }

    @Test fun `merchant keys normalise`() {
        assertEquals("swiggy@ybl", CaptureProcessor.merchantKey("  SWIGGY@ybl "))
        assertNull(CaptureProcessor.merchantKey(null))
        assertNull(CaptureProcessor.merchantKey("   "))
    }
}

// ---------- undo ----------

class UndoEngineTest {

    private fun capturedSetup(): Pair<FakeStore, Long> = runBlocking {
        val store = FakeStore()
        CaptureProcessor(store).process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)
        store to store.actionsOfKind(ActionKinds.SMS_CAPTURED).single().id
    }

    @Test fun `undo of capture deletes the transaction`() = runBlocking {
        val (store, actionId) = capturedSetup()
        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 9_999L))
        assertEquals(0, store.transactions.size)
        assertTrue(store.getAction(actionId)!!.undone)
    }

    @Test fun `undo twice reports already undone`() = runBlocking {
        val (store, actionId) = capturedSetup()
        val engine = UndoEngine(store)
        engine.undo(actionId, 9_999L)
        assertEquals(UndoResult.AlreadyUndone, engine.undo(actionId, 10_000L))
    }

    @Test fun `undo of auto categorisation restores previous category`() = runBlocking {
        val store = FakeStore()
        store.upsertRule("crazzyproduct@axl", categoryId = 42, createdAt = 0)
        CaptureProcessor(store).process("JK-BOBSMS-S", BOB_DEBIT, 1_000_000L)
        val actionId = store.actionsOfKind(ActionKinds.AUTO_CATEGORISED).single().id

        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 9_999L))
        assertNull(store.transactions.values.single().categoryId)
    }

    @Test fun `undo of learned rule deletes the rule`() = runBlocking {
        val store = FakeStore()
        RuleLearner(store).learn("Swiggy@ybl", categoryId = 7, categoryName = "Food & Dining", nowMillis = 0)
        assertEquals(7L, store.categoryIdForMerchant("swiggy@ybl"))
        val actionId = store.actionsOfKind(ActionKinds.RULE_LEARNED).single().id

        assertEquals(UndoResult.Done, UndoEngine(store).undo(actionId, 9_999L))
        assertNull(store.categoryIdForMerchant("swiggy@ybl"))
    }

    @Test fun `undo of autocreated account is blocked while it has transactions`() = runBlocking {
        val (store, _) = capturedSetup()
        val accountAction = store.actionsOfKind(ActionKinds.ACCOUNT_AUTOCREATED).single().id
        val result = UndoEngine(store).undo(accountAction, 9_999L)
        assertTrue(result is UndoResult.Blocked)
        assertEquals(1, store.accounts.size)
    }

    @Test fun `undo on a deleted target is target gone and marks undone`() = runBlocking {
        val (store, actionId) = capturedSetup()
        store.transactions.clear() // someone deleted the transaction directly
        assertEquals(UndoResult.TargetGone, UndoEngine(store).undo(actionId, 9_999L))
        assertTrue(store.getAction(actionId)!!.undone)
    }
}

// ---------- payload codec ----------

class ActionPayloadTest {

    @Test fun `round trips plain values`() {
        val map = mapOf("oldCategoryId" to "", "newCategoryId" to "42")
        assertEquals(map, ActionPayload.decode(ActionPayload.encode(map)))
    }

    @Test fun `round trips values with newlines and backslashes`() {
        val map = mapOf("a" to "line1\nline2", "b" to "back\\slash", "c" to "both\\\nmix")
        assertEquals(map, ActionPayload.decode(ActionPayload.encode(map)))
    }

    @Test fun `empty map round trips`() {
        assertEquals(emptyMap<String, String>(), ActionPayload.decode(ActionPayload.encode(emptyMap())))
    }
}
