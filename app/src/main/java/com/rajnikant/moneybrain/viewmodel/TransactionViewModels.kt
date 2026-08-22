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
import com.rajnikant.moneybrain.data.RoomRuleStore
import com.rajnikant.moneybrain.capture.RuleLearner
import com.rajnikant.moneybrain.capture.RuleStore
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.recurring.applyRecurringMatch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class TimelineItem(
    val transaction: TransactionEntity,
    val account: AccountEntity?,
    val category: CategoryEntity?,
)

sealed interface TimelineEntry {
    data class DayHeader(val date: LocalDate) : TimelineEntry
    data class Row(val item: TimelineItem) : TimelineEntry
}

class TimelineViewModel(
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
) : ViewModel() {
    val entries: Flow<List<TimelineEntry>> = combine(
        transactionDao.observeAll(),
        accountDao.observeAll(),
        categoryDao.observeAll(),
    ) { transactions, accounts, categories ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val timelineItems = transactions.map { transaction ->
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
    var state by mutableStateOf(TransactionEditorState())
        private set

    private var existingTransaction: TransactionEntity? = null
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
                        merchant = transaction.merchant.orEmpty(),
                        notes = transaction.notes.orEmpty(),
                        dateTime = formatDateTime(transaction.occurredAt),
                    )
                }
            }
        } else {
            viewModelScope.launch {
                state = state.copy(accountId = accountDao.observeAllFirstCashId())
            }
        }
    }

    fun update(transform: TransactionEditorState.() -> TransactionEditorState) {
        state = state.transform()
    }

    fun validAmount(): Long? = Money.parseToPaise(state.amount)?.takeIf { it > 0 }

    fun validDateTime(): Long? = parseDateTime(state.dateTime)

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
            )
            database.withTransaction {
                if (previous == null) {
                    val id = transactionDao.insert(transaction)
                    if (transaction.direction == "OUT") transactionDao.getById(id)?.let { applyRecurringMatch(database, it) }
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
            TimelineViewModel(transactionDao, accountDao, categoryDao) as T
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
