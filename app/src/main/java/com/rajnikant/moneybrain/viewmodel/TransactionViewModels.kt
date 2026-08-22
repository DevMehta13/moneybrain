package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rajnikant.moneybrain.data.AccountDao
import com.rajnikant.moneybrain.data.AccountEntity
import com.rajnikant.moneybrain.data.CategoryDao
import com.rajnikant.moneybrain.data.CategoryEntity
import com.rajnikant.moneybrain.data.TransactionDao
import com.rajnikant.moneybrain.data.TransactionEntity
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.data.PersonLedgerEntity
import com.rajnikant.moneybrain.data.RoomRuleStore
import com.rajnikant.moneybrain.capture.RuleLearner
import com.rajnikant.moneybrain.capture.RuleStore
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.people.LedgerKinds
import com.rajnikant.moneybrain.people.SplitMath
import com.rajnikant.moneybrain.recurring.applyRecurringMatch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class TimelineItem(
    val transaction: TransactionEntity,
    val account: AccountEntity?,
    val category: CategoryEntity?,
)
data class TimelineFilters(val search: String = "", val accountId: Long? = null, val categoryId: Long? = null, val bucketId: Long? = null, val tripId: Long? = null, val personId: Long? = null, val direction: String? = null, val uncategorised: Boolean = false)

sealed interface TimelineEntry {
    data class DayHeader(val date: LocalDate) : TimelineEntry
    data class Row(val item: TimelineItem) : TimelineEntry
}

class TimelineViewModel(
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    private val database: MoneyBrainDatabase,
) : ViewModel() {
    val accounts = accountDao.observeAll(); val categories = categoryDao.observeAll(); val buckets = database.bucketDao().observeAll(); val trips = database.tripDao().observeAll(); val people = database.personDao().observeAll()
    private val filters = MutableStateFlow(TimelineFilters())
    val filterState = filters
    fun updateFilters(transform: (TimelineFilters) -> TimelineFilters) { filters.value = transform(filters.value) }
    val entries: Flow<List<TimelineEntry>> = combine(
        transactionDao.observeAll(),
        accountDao.observeAll(),
        categoryDao.observeAll(),
        database.personLedgerDao().observeAll(), filters,
    ) { transactions, accounts, categories, ledger, filter ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val matchingTransactionIds = filter.personId?.let { person -> ledger.filter { it.personId == person && it.kind in setOf("SPLIT", "LENT", "SETTLEMENT") }.mapNotNull { it.transactionId }.toSet() }
        val timelineItems = transactions.filter { transaction ->
            val category = transaction.categoryId?.let(categoriesById::get)
            val searchable = listOfNotNull(transaction.merchant, transaction.notes, category?.name).joinToString(" ").lowercase()
            (filter.search.isBlank() || filter.search.lowercase() in searchable) &&
                (filter.accountId == null || transaction.accountId == filter.accountId) &&
                (filter.categoryId == null || transaction.categoryId == filter.categoryId) &&
                (filter.bucketId == null || transaction.bucketId == filter.bucketId || (transaction.bucketId == null && category?.bucketId == filter.bucketId)) &&
                (filter.tripId == null || transaction.tripId == filter.tripId) &&
                (matchingTransactionIds == null || transaction.id in matchingTransactionIds) &&
                (filter.direction == null || transaction.direction == filter.direction) &&
                (!filter.uncategorised || transaction.categoryId == null)
        }.map { transaction ->
            TimelineItem(
                transaction = transaction,
                account = accountsById[transaction.accountId],
                category = transaction.categoryId?.let(categoriesById::get),
            )
        }
        buildList {
            var previousDate: LocalDate? = null
            timelineItems.forEach { item ->
                val date = item.transaction.occurredAt.toLocalDate()
                if (date != previousDate) {
                    add(TimelineEntry.DayHeader(date))
                    previousDate = date
                }
                add(TimelineEntry.Row(item))
            }
        }
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

data class TransactionEditorState(
    val amount: String = "",
    val direction: String = "OUT",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val bucketId: Long? = null,
    val tripId: Long? = null,
    val merchant: String = "",
    val notes: String = "",
    val dateTime: String = formatDateTime(System.currentTimeMillis()),
)

private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
)

private fun parseDateTime(text: String): Long? = runCatching {
    LocalDateTime.parse(text.trim(), dateTimeFormatter)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

class TransactionEditorViewModel(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionId: Long?,
    private val ruleStore: RuleStore,
    private val database: MoneyBrainDatabase,
) : ViewModel() {
    val accounts = accountDao.observeAll()
    val categories = categoryDao.observeAll()
    val buckets = database.bucketDao().observeAll()
    val people = database.personDao().observeAll()
    val trips = database.tripDao().observeAll()
    var state by mutableStateOf(TransactionEditorState())
        private set

    private var existingTransaction: TransactionEntity? = null
    var splitMode by mutableStateOf("EQUAL")
        private set
    var splitPeople by mutableStateOf<Set<Long>>(emptySet())
        private set
    var customShares by mutableStateOf<Map<Long, String>>(emptyMap())
        private set
    var existingSplits by mutableStateOf<List<PersonLedgerEntity>>(emptyList())
        private set
    private val _finished = Channel<Unit>(Channel.CONFLATED)
    val finished = _finished.receiveAsFlow()

    init {
        if (transactionId != null) {
            viewModelScope.launch {
                transactionDao.getById(transactionId)?.let { transaction ->
                    existingTransaction = transaction
                    state = TransactionEditorState(
                        amount = Money.formatPaise(transaction.amountPaise).removePrefix("₹"),
                        direction = transaction.direction,
                        accountId = transaction.accountId,
                        categoryId = transaction.categoryId,
                        bucketId = transaction.bucketId,
                        tripId = transaction.tripId,
                        merchant = transaction.merchant.orEmpty(),
                        notes = transaction.notes.orEmpty(),
                        dateTime = formatDateTime(transaction.occurredAt),
                    )
                    database.personLedgerDao().observeSplitsForTransaction(transactionId).first().also { splits ->
                        existingSplits = splits
                        splitPeople = splits.map { it.personId }.toSet()
                        customShares = splits.associate { it.personId to Money.formatPaise(it.amountPaise).removePrefix("₹") }
                        splitMode = if (splits.map { it.amountPaise } == SplitMath.equalShares(transaction.amountPaise, splits.size + 1).drop(1)) "EQUAL" else "CUSTOM"
                    }
                }
            }
        } else {
            viewModelScope.launch {
                state = state.copy(
                    accountId = accountDao.observeAllFirstCashId(),
                    tripId = database.tripDao().activeAt(System.currentTimeMillis())?.id,
                )
            }
        }
    }

    fun update(transform: TransactionEditorState.() -> TransactionEditorState) {
        state = state.transform()
    }

    fun validAmount(): Long? = Money.parseToPaise(state.amount)?.takeIf { it > 0 }

    fun validDateTime(): Long? = parseDateTime(state.dateTime)

    fun chooseSplitMode(mode: String) { splitMode = mode }
    fun togglePerson(personId: Long) {
        splitPeople = if (personId in splitPeople) splitPeople - personId else splitPeople + personId
    }
    fun setCustomShare(personId: Long, value: String) { customShares = customShares + (personId to value) }
    fun validSplits(): Boolean {
        if (splitPeople.isEmpty()) return true
        val amount = validAmount() ?: return false
        return if (splitMode == "EQUAL") true else SplitMath.validCustomShares(amount, splitPeople.map { Money.parseToPaise(customShares[it].orEmpty()) ?: 0 })
    }
    fun removeSplit(row: PersonLedgerEntity) {
        viewModelScope.launch {
            database.personLedgerDao().deleteById(row.id)
            existingSplits = existingSplits.filterNot { it.id == row.id }
            splitPeople = splitPeople - row.personId
        }
    }

    fun save(categoryId: Long? = state.categoryId) {
        val amountPaise = validAmount() ?: return
        val accountId = state.accountId ?: return
        val occurredAt = validDateTime() ?: return
        viewModelScope.launch {
            val previous = existingTransaction
            val transaction = TransactionEntity(
                id = previous?.id ?: 0,
                amountPaise = amountPaise,
                direction = state.direction,
                accountId = accountId,
                categoryId = categoryId,
                merchant = state.merchant.trim().ifBlank { null },
                occurredAt = occurredAt,
                notes = state.notes.trim().ifBlank { null },
                source = "MANUAL",
                fingerprint = previous?.fingerprint,
                referenceNo = previous?.referenceNo,
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                bucketId = state.bucketId,
                tripId = if (state.direction == "OUT") state.tripId else null,
            )
            database.withTransaction {
                val savedId = if (previous == null) {
                    val id = transactionDao.insert(transaction)
                    if (transaction.direction == "OUT") transactionDao.getById(id)?.let { applyRecurringMatch(database, it) }
                    id
                } else {
                    transactionDao.update(transaction)
                    if (previous.categoryId != categoryId && transaction.merchant != null && categoryId != null) {
                        categoryDao.getById(categoryId)?.let { category ->
                            RuleLearner(ruleStore).learn(
                                transaction.merchant,
                                categoryId,
                                category.name,
                                System.currentTimeMillis(),
                            )
                        }
                    }
                    transaction.id
                }
                database.personLedgerDao().deleteSplitsForTransaction(savedId)
                val shares = if (splitPeople.isEmpty()) emptyList() else if (splitMode == "EQUAL") {
                    SplitMath.equalShares(amountPaise, splitPeople.size + 1).drop(1)
                } else splitPeople.map { Money.parseToPaise(customShares[it].orEmpty()) ?: 0 }
                splitPeople.toList().zip(shares).forEach { (personId, share) ->
                    database.personLedgerDao().insert(PersonLedgerEntity(personId = personId, amountPaise = share, kind = LedgerKinds.SPLIT, transactionId = savedId, note = null, createdAt = System.currentTimeMillis()))
                }
            }
            _finished.send(Unit)
        }
    }

    fun delete() {
        val transaction = existingTransaction ?: return
        viewModelScope.launch {
            transactionDao.delete(transaction)
            _finished.send(Unit)
        }
    }
}

private suspend fun AccountDao.observeAllFirstCashId(): Long? {
    return observeAll().first().firstOrNull { it.type == "CASH" }?.id
}

class AccountsViewModel(private val accountDao: AccountDao) : ViewModel() {
    val accounts = accountDao.observeAll()

    fun addAccount(name: String, type: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            accountDao.insert(
                AccountEntity(name = name.trim(), type = type, createdAt = System.currentTimeMillis()),
            )
        }
    }
}

class MoneyBrainViewModelFactory(
    private val database: MoneyBrainDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionId: Long? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TimelineViewModel::class.java) ->
            TimelineViewModel(transactionDao, accountDao, categoryDao, database) as T
        modelClass.isAssignableFrom(TransactionEditorViewModel::class.java) ->
            TransactionEditorViewModel(
                transactionDao,
                accountDao,
                categoryDao,
                transactionId,
                RoomRuleStore(database),
                database,
            ) as T
        modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(accountDao) as T
        modelClass.isAssignableFrom(ActivityViewModel::class.java) -> ActivityViewModel(database) as T
        modelClass.isAssignableFrom(BucketsViewModel::class.java) -> BucketsViewModel(database) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
