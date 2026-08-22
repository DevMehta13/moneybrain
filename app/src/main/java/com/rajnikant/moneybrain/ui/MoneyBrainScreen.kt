package com.rajnikant.moneybrain.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rajnikant.moneybrain.MoneyBrainApp
import com.rajnikant.moneybrain.capture.SmsMask
import com.rajnikant.moneybrain.capture.SmsParser
import com.rajnikant.moneybrain.capture.ActionKinds
import com.rajnikant.moneybrain.capture.UndoResult
import com.rajnikant.moneybrain.data.AccountEntity
import com.rajnikant.moneybrain.data.CategoryEntity
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.viewmodel.AccountsViewModel
import com.rajnikant.moneybrain.viewmodel.ActivityViewModel
import com.rajnikant.moneybrain.viewmodel.BucketsViewModel
import com.rajnikant.moneybrain.buckets.BucketMath
import com.rajnikant.moneybrain.viewmodel.MoneyBrainViewModelFactory
import com.rajnikant.moneybrain.viewmodel.TimelineItem
import com.rajnikant.moneybrain.viewmodel.TimelineEntry
import com.rajnikant.moneybrain.viewmodel.TimelineViewModel
import com.rajnikant.moneybrain.viewmodel.TransactionEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val timelineRoute = "timeline"
private const val settingsRoute = "settings"
private const val activityRoute = "activity"
private const val bucketsRoute = "buckets"
private const val accountsRoute = "accounts"
private const val captureRoute = "capture"
private const val categoryBucketsRoute = "categoryBuckets"
private const val addRoute = "add"
private const val editRoute = "edit/{transactionId}"

@Composable
fun MoneyBrainScreen() {
    val context = LocalContext.current
    val database = (context.applicationContext as MoneyBrainApp).database
    val factory = remember(database) {
        MoneyBrainViewModelFactory(
            database = database,
            transactionDao = database.transactionDao(),
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
        )
    }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: timelineRoute
    val isTopLevel = route == timelineRoute || route == activityRoute || route == bucketsRoute || route == settingsRoute

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
                SettingsScreen(
                    onAccounts = { navController.navigate(accountsRoute) },
                    onCapture = { navController.navigate(captureRoute) },
                    onCategoryBuckets = { navController.navigate(categoryBucketsRoute) },
                )
            }
            composable(categoryBucketsRoute) { CategoryBucketsScreen(database, onBack = { navController.popBackStack() }) }
            composable(activityRoute) {
                val viewModel: ActivityViewModel = viewModel(factory = factory)
                ActivityScreen(viewModel, onAddManually = { navController.navigate(addRoute) })
            }
            composable(bucketsRoute) { val viewModel: BucketsViewModel = viewModel(factory = factory); BucketsScreen(viewModel) }
            composable(accountsRoute) {
                val viewModel: AccountsViewModel = viewModel(factory = factory)
                AccountsScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(captureRoute) {
                CaptureScreen(onBack = { navController.popBackStack() })
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
                        database = database,
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
            onClick = { navController.navigateTopLevel(timelineRoute) },
            icon = { Text("•") },
            label = { Text("Timeline") },
        )
        NavigationBarItem(
            selected = route == activityRoute,
            onClick = { navController.navigateTopLevel(activityRoute) },
            icon = { Text("•") },
            label = { Text("Activity") },
        )
        NavigationBarItem(
            selected = route == bucketsRoute,
            onClick = { navController.navigateTopLevel(bucketsRoute) }, icon = { Text("•") }, label = { Text("Buckets") },
        )
        NavigationBarItem(
            selected = route == settingsRoute,
            onClick = { navController.navigateTopLevel(settingsRoute) },
            icon = { Text("•") },
            label = { Text("Settings") },
        )
    }
}

@Composable
private fun BucketsScreen(viewModel: BucketsViewModel) {
    val statuses by viewModel.status.collectAsState(initial = emptyList())
    val plans by viewModel.plans.collectAsState(initial = emptyList())
    val salaries by viewModel.salaryCandidates.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Buckets", style = MaterialTheme.typography.headlineSmall) }
        salaries.forEach { salary ->
            item {
                Card { Column(Modifier.padding(16.dp)) {
                    Text("Salary detected: ${Money.formatPaise(salary.amountPaise)} — split into buckets?")
                    Button(onClick = { viewModel.splitSalary(salary.id, salary.amountPaise, salary.occurredAt) }) { Text("Split now") }
                } }
            }
        }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New bucket") }); Button(onClick = { viewModel.addBucket(name); name = "" }, enabled = name.isNotBlank()) { Text("Add bucket") } }
        items(statuses, key = { it.bucket.id }) { status ->
            val remaining = BucketMath.remaining(status.allocated, status.spent, 0)
            Card { Column(Modifier.padding(16.dp)) { Text(status.bucket.name, fontWeight = FontWeight.Medium); Text("Allocated ${Money.formatPaise(status.allocated)} · Spent ${Money.formatPaise(status.spent)} · Remaining ${Money.formatPaise(remaining)}", color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text("Plan: ${plans.filter { it.bucketId == status.bucket.id }.joinToString { if (it.kind == "PERCENT") "${it.value / 100}%" else Money.formatPaise(it.value) }}") } }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TimelineScreen(viewModel: TimelineViewModel, onTransaction: (Long) -> Unit) {
    val entries by viewModel.entries.collectAsState(initial = emptyList())
    if (entries.isEmpty()) {
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
        items(
            items = entries,
            key = { entry ->
                when (entry) {
                    is TimelineEntry.DayHeader -> "h-${entry.date}"
                    is TimelineEntry.Row -> "t-${entry.item.transaction.id}"
                }
            },
        ) { entry ->
            when (entry) {
                is TimelineEntry.DayHeader -> Text(
                    text = entry.date.timelineHeader(),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                is TimelineEntry.Row -> {
                    val item = entry.item
                    TransactionRow(item, onClick = { onTransaction(item.transaction.id) })
                }
            }
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
            if (transaction.source == "SMS") Text("auto", style = MaterialTheme.typography.labelSmall)
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
private fun ActivityScreen(viewModel: ActivityViewModel, onAddManually: () -> Unit) {
    val actions by viewModel.actions.collectAsState(initial = emptyList())
    val unresolved by viewModel.unresolvedMessages.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    var captureToUndo by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.undoResults.collect { result ->
            if (result is UndoResult.Blocked) snackbarHostState.showSnackbar(result.reason)
        }
    }
    if (captureToUndo != null) {
        AlertDialog(
            onDismissRequest = { captureToUndo = null },
            title = { Text("Remove this recorded payment?") },
            text = { Text("The SMS itself is untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.undo(captureToUndo!!)
                    captureToUndo = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { captureToUndo = null }) { Text("Cancel") } },
        )
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Activity", style = MaterialTheme.typography.headlineSmall) }
            item { Text("Needs attention", style = MaterialTheme.typography.titleMedium) }
            if (unresolved.isEmpty()) {
                item { Text("Nothing needs attention.") }
            } else {
                items(unresolved, key = { it.id }) { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(SmsMask.mask(message.sender), fontWeight = FontWeight.Medium)
                            Text(SmsMask.mask(message.body))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.dismissMessage(message.id) }) { Text("Dismiss") }
                                TextButton(onClick = onAddManually) { Text("Add manually") }
                            }
                        }
                    }
                }
            }
            item { Text("Action log", style = MaterialTheme.typography.titleMedium) }
            if (actions.isEmpty()) {
                item { Text("No automatic actions yet.") }
            } else {
                items(actions, key = { it.id }) { action ->
                    val undone = action.undoneAt != null
                    Card(modifier = Modifier.fillMaxWidth().alpha(if (undone) 0.55f else 1f)) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(action.description, fontWeight = FontWeight.Medium)
                                Text(action.createdAt.activityTime(), style = MaterialTheme.typography.bodySmall)
                                if (undone) Text("Undone", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!undone) {
                                TextButton(onClick = {
                                    if (action.kind == ActionKinds.SMS_CAPTURED) captureToUndo = action.id
                                    else viewModel.undo(action.id)
                                }) { Text("Undo") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Long.activityTime(): String {
    val elapsedMinutes = ((System.currentTimeMillis() - this) / 60_000).coerceAtLeast(0)
    return when {
        elapsedMinutes < 1 -> "Just now"
        elapsedMinutes < 60 -> "$elapsedMinutes min ago"
        elapsedMinutes < 1_440 -> "${elapsedMinutes / 60} hr ago"
        else -> Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
    }
}

@Composable
private fun TransactionEditorScreen(
    viewModel: TransactionEditorViewModel,
    isEdit: Boolean,
    onDone: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val buckets by viewModel.buckets.collectAsState(initial = emptyList())
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
        item { BucketPicker(buckets, state.bucketId) { id -> viewModel.update { copy(bucketId = id) } } }
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
private fun BucketPicker(buckets: List<com.rajnikant.moneybrain.data.BucketEntity>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Bucket override", style = MaterialTheme.typography.labelLarge)
        Button(onClick = { expanded = true }) { Text(buckets.firstOrNull { it.id == selectedId }?.name ?: "From category") }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("From category") }, onClick = { onSelect(null); expanded = false })
            buckets.forEach { bucket -> androidx.compose.material3.DropdownMenuItem(text = { Text(bucket.name) }, onClick = { onSelect(bucket.id); expanded = false }) }
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
private fun SettingsScreen(onAccounts: () -> Unit, onCapture: () -> Unit, onCategoryBuckets: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onAccounts)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium)
                Text("View or add bank, card, and cash accounts")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onCapture)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SMS capture (setup)", style = MaterialTheme.typography.titleMedium)
                Text("Review masked bank SMS samples")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onCategoryBuckets)) {
            Column(modifier = Modifier.padding(16.dp)) { Text("Categories & buckets", style = MaterialTheme.typography.titleMedium); Text("Choose which bucket each category drains") }
        }
    }
}

@Composable
private fun CategoryBucketsScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, onBack: () -> Unit) {
    val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList())
    val buckets by database.bucketDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("Categories & buckets", style = MaterialTheme.typography.headlineSmall) }
        items(categories, key = { it.id }) { category ->
            var expanded by remember { mutableStateOf(false) }
            Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.name); Button(onClick = { expanded = true }) { Text(buckets.firstOrNull { it.id == category.bucketId }?.name ?: "None") }
                androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("None") }, onClick = { scope.launch { database.categoryDao().update(category.copy(bucketId = null)) }; expanded = false })
                    buckets.forEach { bucket -> androidx.compose.material3.DropdownMenuItem(text = { Text(bucket.name) }, onClick = { scope.launch { database.categoryDao().update(category.copy(bucketId = bucket.id)) }; expanded = false }) }
                }
            } }
        }
    }
}

private data class CapturedMessage(
    val sender: String,
    val maskedBody: String,
    val date: Long,
    val recognised: Boolean,
)

@Composable
private fun CaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(emptyList<CapturedMessage>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionGranted = grants[Manifest.permission.READ_SMS] == true && grants[Manifest.permission.RECEIVE_SMS] == true
        permissionDenied = !permissionGranted
    }

    LaunchedEffect(permissionGranted) {
        messages = if (permissionGranted) {
            withContext(Dispatchers.IO) { readBankMessages(context.contentResolver) }
        } else {
            emptyList()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (!permissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("SMS capture", style = MaterialTheme.typography.headlineSmall)
                Text("Money Brain reads bank SMS to record payments automatically. Messages never leave this phone unmasked.")
                if (permissionDenied) {
                    Text("SMS permission was not granted. You can try again whenever you are ready.")
                }
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                }) {
                    Text("Grant permission")
                }
            }
        } else {
            val recognisedCount = messages.count { it.recognised }
            val unrecognised = messages.filterNot { it.recognised }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TextButton(onClick = onBack) { Text("Back") }
                }
                item {
                    Text("SMS capture", style = MaterialTheme.typography.headlineSmall)
                    Text("Bank messages: ${messages.size} · recognised: $recognisedCount · unrecognised: ${unrecognised.size}")
                }
                item {
                    Button(
                        onClick = {
                            val samples = unrecognised.take(30).joinToString("\n\n") { message ->
                                "[${message.sender}]\n${message.maskedBody}"
                            }
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Masked bank SMS samples", samples))
                            scope.launch { snackbarHostState.showSnackbar("Masked samples copied") }
                        },
                        enabled = unrecognised.isNotEmpty(),
                    ) { Text("Copy masked samples") }
                }
                items(messages, key = { "${it.date}-${it.sender}-${it.maskedBody}" }) { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(message.sender, fontWeight = FontWeight.Medium)
                            Text(if (message.recognised) "Recognised" else "Unrecognised")
                            Text(message.maskedBody)
                            Text(
                                Instant.ofEpochMilli(message.date)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readBankMessages(contentResolver: ContentResolver): List<CapturedMessage> {
    return try {
        contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC",
        )?.use { cursor ->
            val senderIndex = cursor.getColumnIndexOrThrow("address")
            val bodyIndex = cursor.getColumnIndexOrThrow("body")
            val dateIndex = cursor.getColumnIndexOrThrow("date")
            buildList {
                var rowsScanned = 0
                while (rowsScanned < 500 && cursor.moveToNext()) {
                    rowsScanned += 1
                    val sender = cursor.getString(senderIndex).orEmpty()
                    if (!SmsParser.isBankSender(sender)) continue
                    val rawBody = cursor.getString(bodyIndex).orEmpty()
                    add(
                        CapturedMessage(
                            sender = SmsMask.mask(sender),
                            maskedBody = SmsMask.mask(rawBody),
                            date = cursor.getLong(dateIndex),
                            recognised = SmsParser.parse(sender, rawBody) != null,
                        ),
                    )
                }
            }
        }.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
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

private fun LocalDate.timelineHeader(): String = when (this) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
}
