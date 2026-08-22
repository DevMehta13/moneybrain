package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rajnikant.moneybrain.data.AccountDao
import com.rajnikant.moneybrain.data.AccountEntity
import com.rajnikant.moneybrain.data.CategoryDao
import com.rajnikant.moneybrain.data.CategoryEntity
import com.rajnikant.moneybrain.data.TransactionDao
import com.rajnikant.moneybrain.data.TransactionEntity
import com.rajnikant.moneybrain.money.Money
import java.time.Instant
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

class TimelineViewModel(
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
) : ViewModel() {
    val items: Flow<List<TimelineItem>> = combine(
        transactionDao.observeAll(),
        accountDao.observeAll(),
        categoryDao.observeAll(),
    ) { transactions, accounts, categories ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        transactions.map { transaction ->
            TimelineItem(
                transaction = transaction,
                account = accountsById[transaction.accountId],
                category = transaction.categoryId?.let(categoriesById::get),
            )
        }
    }
}

data class TransactionEditorState(
    val amount: String = "",
    val direction: String = "OUT",
    val accountId: Long? = null,
    val categoryId: Long? = null,
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
) : ViewModel() {
    val accounts = accountDao.observeAll()
    val categories = categoryDao.observeAll()
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
            )
            if (previous == null) transactionDao.insert(transaction) else transactionDao.update(transaction)
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
            TransactionEditorViewModel(transactionDao, accountDao, categoryDao, transactionId) as T
        modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(accountDao) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
