package com.rajnikant.moneybrain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rajnikant.moneybrain.MoneyBrainApp
import com.rajnikant.moneybrain.data.AccountEntity
import com.rajnikant.moneybrain.data.CategoryEntity
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.viewmodel.AccountsViewModel
import com.rajnikant.moneybrain.viewmodel.MoneyBrainViewModelFactory
import com.rajnikant.moneybrain.viewmodel.TimelineItem
import com.rajnikant.moneybrain.viewmodel.TimelineViewModel
import com.rajnikant.moneybrain.viewmodel.TransactionEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val timelineRoute = "timeline"
private const val settingsRoute = "settings"
private const val accountsRoute = "accounts"
private const val addRoute = "add"
private const val editRoute = "edit/{transactionId}"

@Composable
fun MoneyBrainScreen() {
    val context = LocalContext.current
    val database = (context.applicationContext as MoneyBrainApp).database
    val factory = remember(database) {
        MoneyBrainViewModelFactory(
            transactionDao = database.transactionDao(),
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
        )
    }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: timelineRoute
    val isTopLevel = route == timelineRoute || route == settingsRoute

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                BottomBar(navController, route)
            }
        },
        floatingActionButton = {
            if (route == timelineRoute) {
                FloatingActionButton(onClick = { navController.navigate(addRoute) }) {
                    Text("+")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = timelineRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(timelineRoute) {
                val viewModel: TimelineViewModel = viewModel(factory = factory)
                TimelineScreen(viewModel) { id -> navController.navigate("edit/$id") }
            }
            composable(settingsRoute) {
                SettingsScreen(onAccounts = { navController.navigate(accountsRoute) })
            }
            composable(accountsRoute) {
                val viewModel: AccountsViewModel = viewModel(factory = factory)
                AccountsScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(addRoute) {
                val viewModel: TransactionEditorViewModel = viewModel(factory = factory)
                TransactionEditorScreen(
                    viewModel = viewModel,
                    isEdit = false,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(editRoute) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId")?.toLongOrNull()
                    ?: return@composable
                val editFactory = remember(factory, transactionId) {
                    MoneyBrainViewModelFactory(
                        transactionDao = database.transactionDao(),
                        accountDao = database.accountDao(),
                        categoryDao = database.categoryDao(),
                        transactionId = transactionId,
                    )
                }
                val viewModel: TransactionEditorViewModel = viewModel(factory = editFactory)
                TransactionEditorScreen(
                    viewModel = viewModel,
                    isEdit = true,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, route: String) {
    NavigationBar {
        NavigationBarItem(
            selected = route == timelineRoute,
            onClick = { navController.navigate(timelineRoute) { launchSingleTop = true } },
            icon = { Text("•") },
            label = { Text("Timeline") },
        )
        NavigationBarItem(
            selected = route == settingsRoute,
            onClick = { navController.navigate(settingsRoute) { launchSingleTop = true } },
            icon = { Text("•") },
            label = { Text("Settings") },
        )
    }
}

@Composable
private fun TimelineScreen(viewModel: TimelineViewModel, onTransaction: (Long) -> Unit) {
    val items by viewModel.items.collectAsState(initial = emptyList())
    if (items.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
            Text("Tap + to add a cash expense.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        var lastDate: LocalDate? = null
        items(items, key = { it.transaction.id }) { item ->
            val date = item.transaction.occurredAt.toLocalDate()
            if (date != lastDate) {
                lastDate = date
                Text(
                    text = date.timelineHeader(),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TransactionRow(item, onClick = { onTransaction(item.transaction.id) })
        }
    }
}

@Composable
private fun TransactionRow(item: TimelineItem, onClick: () -> Unit) {
    val transaction = item.transaction
    val detail = listOfNotNull(transaction.merchant, transaction.notes).joinToString(" · ")
    val amount = Money.formatPaise(transaction.amountPaise)
    val isIncome = transaction.direction == "IN"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.category?.name ?: "Uncategorised", fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
            Text(item.account?.name ?: "Unknown account", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = if (isIncome) "+$amount" else amount,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider()
}

@Composable
private fun TransactionEditorScreen(
    viewModel: TransactionEditorViewModel,
    isEdit: Boolean,
    onDone: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val state = viewModel.state
    val focusRequester = remember { FocusRequester() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.finished.collect { onDone() }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::delete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(if (isEdit) "Edit transaction" else "Add transaction", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(
                value = state.amount,
                onValueChange = { value -> viewModel.update { copy(amount = value) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.amount.isNotBlank() && viewModel.validAmount() == null,
            )
        }
        item {
            DirectionPicker(state.direction) { direction ->
                viewModel.update { copy(direction = direction) }
            }
        }
        item {
            AccountPicker(accounts, state.accountId) { accountId ->
                viewModel.update { copy(accountId = accountId) }
            }
        }
        item {
            OutlinedTextField(
                value = state.merchant,
                onValueChange = { value -> viewModel.update { copy(merchant = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant / label (optional)") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = state.notes,
                onValueChange = { value -> viewModel.update { copy(notes = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
            )
        }
        item {
            OutlinedTextField(
                value = state.dateTime,
                onValueChange = { value -> viewModel.update { copy(dateTime = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date & time") },
                supportingText = { Text("YYYY-MM-DD HH:MM") },
                isError = viewModel.validDateTime() == null,
                singleLine = true,
            )
        }
        item { Text(if (isEdit) "Category" else "Tap a category to save", fontWeight = FontWeight.Medium) }
        item {
            CategoryPicker(
                categories = categories,
                selectedCategoryId = state.categoryId,
                onCategory = { categoryId ->
                    if (isEdit) viewModel.update { copy(categoryId = categoryId) }
                    else viewModel.save(categoryId)
                },
                enabled = viewModel.validAmount() != null && state.accountId != null && viewModel.validDateTime() != null,
            )
        }
        if (isEdit) {
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = viewModel.validAmount() != null && state.accountId != null && viewModel.validDateTime() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
            item {
                TextButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DirectionPicker(direction: String, onDirection: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf("OUT", "IN").forEachIndexed { index, option ->
            SegmentedButton(
                selected = direction == option,
                onClick = { onDirection(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) { Text(option) }
        }
    }
}

@Composable
private fun AccountPicker(accounts: List<AccountEntity>, selectedId: Long?, onAccount: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Column {
        Text("Account", style = MaterialTheme.typography.labelLarge)
        Button(onClick = { expanded = true }) { Text(selected?.name ?: "Choose account") }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${account.name} (${account.type})") },
                    onClick = {
                        onAccount(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onCategory: (Long) -> Unit,
    enabled: Boolean,
) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selectedCategoryId,
                onClick = { onCategory(category.id) },
                enabled = enabled,
                label = { Text(category.name) },
            )
        }
    }
}

@Composable
private fun SettingsScreen(onAccounts: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onAccounts)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium)
                Text("View or add bank, card, and cash accounts")
            }
        }
    }
}

@Composable
private fun AccountsScreen(viewModel: AccountsViewModel, onBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CASH") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Accounts", style = MaterialTheme.typography.headlineSmall)
        accounts.forEach { account -> Text("${account.name} · ${account.type}") }
        HorizontalDivider()
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account name") })
        AccountTypePicker(type) { type = it }
        Button(
            onClick = {
                viewModel.addAccount(name, type)
                name = ""
            },
            enabled = name.isNotBlank(),
        ) { Text("Add account") }
    }
}

@Composable
private fun AccountTypePicker(type: String, onType: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf("BANK", "CARD", "CASH").forEachIndexed { index, option ->
            SegmentedButton(
                selected = type == option,
                onClick = { onType(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
            ) { Text(option) }
        }
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

private fun LocalDate.timelineHeader(): String = when (this) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
}
