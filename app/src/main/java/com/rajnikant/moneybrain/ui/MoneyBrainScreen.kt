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
import androidx.room.withTransaction
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
import com.rajnikant.moneybrain.data.PersonLedgerEntity
import com.rajnikant.moneybrain.data.TransactionEntity
import com.rajnikant.moneybrain.money.Money
import com.rajnikant.moneybrain.people.LedgerKinds
import com.rajnikant.moneybrain.people.SplitMath
import com.rajnikant.moneybrain.viewmodel.AccountsViewModel
import com.rajnikant.moneybrain.viewmodel.ActivityViewModel
import com.rajnikant.moneybrain.viewmodel.BucketsViewModel
import com.rajnikant.moneybrain.viewmodel.BucketMessage
import com.rajnikant.moneybrain.buckets.BucketMath
import com.rajnikant.moneybrain.buckets.PlanEntry
import com.rajnikant.moneybrain.data.RecurringEntity
import com.rajnikant.moneybrain.recurring.Cadences
import com.rajnikant.moneybrain.recurring.RecurringMath
import com.rajnikant.moneybrain.recurring.RecurringStatus
import com.rajnikant.moneybrain.recurring.toItem
import com.rajnikant.moneybrain.recurring.RecurringDetector
import com.rajnikant.moneybrain.recurring.Occurrence
import com.rajnikant.moneybrain.capture.CaptureProcessor
import com.rajnikant.moneybrain.capture.ActionPayload
import com.rajnikant.moneybrain.capture.PayloadKeys
import com.rajnikant.moneybrain.data.ActionEntity
import com.rajnikant.moneybrain.data.RecurringDismissedEntity
import com.rajnikant.moneybrain.viewmodel.MoneyBrainViewModelFactory
import com.rajnikant.moneybrain.viewmodel.TimelineItem
import com.rajnikant.moneybrain.viewmodel.TimelineEntry
import com.rajnikant.moneybrain.viewmodel.TimelineViewModel
import com.rajnikant.moneybrain.viewmodel.TransactionEditorViewModel
import com.rajnikant.moneybrain.summary.activeTripSummary
import com.rajnikant.moneybrain.summary.peopleSummary
import com.rajnikant.moneybrain.summary.upcomingRecurring
import com.rajnikant.moneybrain.summary.bucketStatuses
import com.rajnikant.moneybrain.summary.detectedRecurring
import com.rajnikant.moneybrain.summary.tripTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val timelineRoute = "timeline"
private const val overviewRoute = "overview"
private const val settingsRoute = "settings"
private const val activityRoute = "activity"
private const val bucketsRoute = "buckets"
private const val recurringRoute = "recurring"
private const val accountsRoute = "accounts"
private const val captureRoute = "capture"
private const val categoryBucketsRoute = "categoryBuckets"
private const val peopleRoute = "people"
private const val tripsRoute = "trips"
private const val personDetailRoute = "person/{personId}"
private const val tripDetailRoute = "trip/{tripId}"
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
    var pendingUncategorised by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: overviewRoute
    val isTopLevel = route == overviewRoute || route == timelineRoute || route == bucketsRoute || route == recurringRoute || route == settingsRoute

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
            startDestination = overviewRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(overviewRoute) { OverviewScreen(database, onBuckets = { navController.navigateTopLevel(bucketsRoute) }, onRecurring = { navController.navigateTopLevel(recurringRoute) }, onTrip = { navController.navigate("trip/$it") }, onPeople = { navController.navigate(peopleRoute) }, onTransaction = { navController.navigate("edit/$it") }, onTimeline = { uncategorised -> pendingUncategorised = uncategorised; navController.navigateTopLevel(timelineRoute) }, onActivity = { navController.navigate(activityRoute) }) }
            composable(timelineRoute) {
                val viewModel: TimelineViewModel = viewModel(factory = factory)
                LaunchedEffect(pendingUncategorised) { if (pendingUncategorised) { viewModel.updateFilters { it.copy(uncategorised = true) }; pendingUncategorised = false } }
                TimelineScreen(viewModel) { id -> navController.navigate("edit/$id") }
            }
            composable(settingsRoute) {
                SettingsScreen(
                    onAccounts = { navController.navigate(accountsRoute) },
                    onCapture = { navController.navigate(captureRoute) },
                    onCategoryBuckets = { navController.navigate(categoryBucketsRoute) },
                    onPeople = { navController.navigate(peopleRoute) }, onTrips = { navController.navigate(tripsRoute) }, onActivity = { navController.navigate(activityRoute) },
                )
            }
            composable(categoryBucketsRoute) { CategoryBucketsScreen(database, onBack = { navController.popBackStack() }) }
            composable(peopleRoute) { PeopleScreen(database, onBack = { navController.popBackStack() }, onPerson = { navController.navigate("person/$it") }) }
            composable(personDetailRoute) { entry -> PersonDetailScreen(database, entry.arguments?.getString("personId")?.toLongOrNull() ?: return@composable, onBack = { navController.popBackStack() }) }
            composable(tripsRoute) { TripsScreen(database, onBack = { navController.popBackStack() }, onTrip = { navController.navigate("trip/$it") }) }
            composable(tripDetailRoute) { entry -> TripDetailScreen(database, entry.arguments?.getString("tripId")?.toLongOrNull() ?: return@composable, onBack = { navController.popBackStack() }) }
            composable(activityRoute) {
                val viewModel: ActivityViewModel = viewModel(factory = factory)
                ActivityScreen(viewModel, onAddManually = { navController.navigate(addRoute) })
            }
            composable(bucketsRoute) { val viewModel: BucketsViewModel = viewModel(factory = factory); BucketsScreen(viewModel) }
            composable(recurringRoute) { RecurringScreen(database) }
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
            selected = route == overviewRoute,
            onClick = { navController.navigateTopLevel(overviewRoute) },
            icon = { Text("⌂") },
            label = { Text("Overview") },
        )
        NavigationBarItem(
            selected = route == timelineRoute,
            onClick = { navController.navigateTopLevel(timelineRoute) },
            icon = { Text("≡") },
            label = { Text("Timeline") },
        )
        NavigationBarItem(
            selected = route == bucketsRoute,
            onClick = { navController.navigateTopLevel(bucketsRoute) }, icon = { Text("▣") }, label = { Text("Buckets") },
        )
        NavigationBarItem(selected = route == recurringRoute, onClick = { navController.navigateTopLevel(recurringRoute) }, icon = { Text("↻") }, label = { Text("Recurring") })
        NavigationBarItem(
            selected = route == settingsRoute,
            onClick = { navController.navigateTopLevel(settingsRoute) },
            icon = { Text("⚙") },
            label = { Text("Settings") },
        )
    }
}

@Composable
private fun OverviewScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, onBuckets: () -> Unit, onRecurring: () -> Unit, onTrip: (Long) -> Unit, onPeople: () -> Unit, onTransaction: (Long) -> Unit, onTimeline: (Boolean) -> Unit, onActivity: () -> Unit) {
    val buckets by database.bucketDao().observeAll().collectAsState(initial = emptyList())
    val allocations by database.bucketAllocationDao().observeMonth(java.time.YearMonth.now().toString()).collectAsState(initial = emptyList())
    val transactions by database.transactionDao().observeAll().collectAsState(initial = emptyList())
    val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList())
    val recurring by database.recurringDao().observeAll().collectAsState(initial = emptyList())
    val dismissed by database.recurringDismissedDao().observeAll().collectAsState(initial = emptyList())
    val trips by database.tripDao().observeAll().collectAsState(initial = emptyList())
    val peopleBalances by database.personLedgerDao().observeBalances().collectAsState(initial = emptyList())
    val unresolved by database.unparsedSmsDao().observeUnresolved().collectAsState(initial = emptyList())
    val statuses = bucketStatuses(buckets, allocations, transactions, categories, recurring, java.time.YearMonth.now().toString())
    val upcoming = upcomingRecurring(recurring, java.time.LocalDate.now().toString())
    val trip = activeTripSummary(trips, transactions)
    val people = peopleSummary(peopleBalances)
    val uncategorised = transactions.count { it.categoryId == null }
    val detected = detectedRecurring(recurring, transactions, dismissed, ZoneId.systemDefault())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (unresolved.isNotEmpty() || uncategorised > 0 || detected.isNotEmpty()) item { Card { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { if (unresolved.isNotEmpty()) TextButton(onClick = onActivity) { Text("${unresolved.size} unrecognised SMS") }; if (uncategorised > 0) TextButton(onClick = { onTimeline(true) }) { Text("$uncategorised uncategorised") }; if (detected.isNotEmpty()) TextButton(onClick = onRecurring) { Text("${detected.size} detected recurring") } } } }
        item { Text("Overview", style = MaterialTheme.typography.headlineSmall) }
        item { Card(Modifier.fillMaxWidth().clickable(onClick = onBuckets)) { Column(Modifier.padding(12.dp)) { Text("Buckets", fontWeight = FontWeight.Medium); statuses.forEach { status -> Text("${status.bucket.name} · Remaining ${Money.formatPaise(status.remaining)}", color = if (status.remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) } } } }
        item { Card(Modifier.fillMaxWidth().clickable(onClick = onRecurring)) { Column(Modifier.padding(12.dp)) { Text("Upcoming bills", fontWeight = FontWeight.Medium); Text("This month ${Money.formatPaise(recurring.filter { it.status == RecurringStatus.ACTIVE && it.nextDue.startsWith(java.time.YearMonth.now().toString()) }.sumOf { it.expectedAmountPaise })}"); upcoming.take(3).forEach { Text("${it.name} · ${Money.formatPaise(it.expectedAmountPaise)} · ${it.nextDueIso}") } } } }
        trip?.let { active -> item { Card(Modifier.fillMaxWidth().clickable { onTrip(active.trip.id) }) { Column(Modifier.padding(12.dp)) { Text("Active trip", fontWeight = FontWeight.Medium); Text("${active.trip.name} · ${Money.formatPaise(active.total)}") } } } }
        item { Card(Modifier.fillMaxWidth().clickable(onClick = onPeople)) { Column(Modifier.padding(12.dp)) { Text("People", fontWeight = FontWeight.Medium); Text("Owed to you ${Money.formatPaise(people.owedToYou)} · You owe ${Money.formatPaise(people.youOwe)}") } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Recent transactions", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { onTimeline(false) }) { Text("See all") } } }
        items(transactions.take(5), key = { it.id }) { transaction -> val category = categories.firstOrNull { it.id == transaction.categoryId }; TransactionRow(TimelineItem(transaction, null, category), onClick = { onTransaction(transaction.id) }) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Recent activity", style = MaterialTheme.typography.titleMedium); TextButton(onClick = onActivity) { Text("See all") } } }
    }
}

@Composable
private fun BucketsScreen(viewModel: BucketsViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshMonth() }
    val statuses by viewModel.status.collectAsState(initial = emptyList())
    val plans by viewModel.plans.collectAsState(initial = emptyList())
    val salaries by viewModel.salaryCandidates.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar((message as BucketMessage.Text).value)
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Buckets", style = MaterialTheme.typography.headlineSmall) }
            salaries.forEach { salary ->
                item {
                    val plan = plans.map { PlanEntry(it.bucketId, it.kind, it.value) }
                    val preview = BucketMath.split(salary.amountPaise, plan)
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Salary detected: ${Money.formatPaise(salary.amountPaise)} — split into buckets?")
                        preview.lines.forEach { line ->
                            Text("${statuses.firstOrNull { it.bucket.id == line.bucketId }?.bucket?.name ?: "Deleted bucket"}: ${Money.formatPaise(line.amountPaise)}")
                        }
                        Text("Unallocated: ${Money.formatPaise(preview.unallocatedPaise)}")
                        Button(onClick = { viewModel.splitSalary(salary.id, salary.amountPaise, salary.occurredAt) }) { Text("Split now") }
                    } }
                }
            }
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New bucket") }); Button(onClick = { viewModel.addBucket(name); name = "" }, enabled = name.isNotBlank()) { Text("Add bucket") } }
            val planEntries = plans.map { PlanEntry(it.bucketId, it.kind, it.value) }
            item { Text("Plan totals: ${BucketMath.totalPercentBp(planEntries) / 100}% · Fixed ${Money.formatPaise(BucketMath.totalFixedPaise(planEntries))}") }
            if (BucketMath.totalPercentBp(planEntries) > 10_000) {
                item { Text("Plan percentages exceed 100%; entries will be capped in order.", color = MaterialTheme.colorScheme.error) }
            }
            items(statuses, key = { it.bucket.id }) { status ->
                val remaining = BucketMath.remaining(status.allocated, status.spent, status.reserved)
                val bucketPlans = plans.filter { it.bucketId == status.bucket.id }
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(status.bucket.name, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { viewModel.deleteBucket(status.bucket.id) }) { Text("Remove") }
                    }
                    Text("Allocated ${Money.formatPaise(status.allocated)} · Spent ${Money.formatPaise(status.spent)}${if (status.reserved > 0) " · Reserved ${Money.formatPaise(status.reserved)}" else ""} · Remaining ${Money.formatPaise(remaining)}", color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    bucketPlans.forEach { entry ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (entry.kind == "PERCENT") "${entry.value / 100}%" else Money.formatPaise(entry.value))
                            TextButton(onClick = { viewModel.deletePlan(entry.id) }) { Text("Remove") }
                        }
                    }
                    PlanEntryAdder(onPercent = { viewModel.addPercent(status.bucket.id, it) }, onFixed = { viewModel.addFixed(status.bucket.id, it) })
                } }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Split order", style = MaterialTheme.typography.titleMedium)
                    Text("When the plan asks for more than the salary has, entries higher in this list are filled first.")
                    plans.forEachIndexed { index, entry ->
                        val bucketName = statuses.firstOrNull { it.bucket.id == entry.bucketId }?.bucket?.name ?: "Deleted bucket"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${index + 1}. $bucketName — ${if (entry.kind == "PERCENT") "${entry.value / 100}%" else Money.formatPaise(entry.value)}")
                            Row {
                                TextButton(onClick = { viewModel.movePlan(plans, entry.id, -1) }, enabled = index > 0) { Text("Up") }
                                TextButton(onClick = { viewModel.movePlan(plans, entry.id, 1) }, enabled = index < plans.lastIndex) { Text("Down") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase) {
    val items by database.recurringDao().observeAll().collectAsState(initial = emptyList())
    val buckets by database.bucketDao().observeAll().collectAsState(initial = emptyList())
    val transactions by database.transactionDao().observeAll().collectAsState(initial = emptyList())
    val dismissed by database.recurringDismissedDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; var merchantKey by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf(Cadences.MONTHLY) }; var bucketId by remember { mutableStateOf<Long?>(null) }; var editingId by remember { mutableStateOf<Long?>(null) }; var cancelId by remember { mutableStateOf<Long?>(null) }; var deleteId by remember { mutableStateOf<Long?>(null) }
    val today = java.time.LocalDate.now().toString()
    val upcoming = upcomingRecurring(items, today, 30)
    val sixMonthsAgo = System.currentTimeMillis() - 183L * 24 * 60 * 60 * 1000
    val candidates = detectedRecurring(items, transactions, dismissed, ZoneId.systemDefault())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Recurring", style = MaterialTheme.typography.headlineSmall); Text("This month: ${Money.formatPaise(items.filter { it.status == RecurringStatus.ACTIVE && it.nextDue.startsWith(java.time.YearMonth.now().toString()) }.sumOf { it.expectedAmountPaise })}") }
        item { Text("Upcoming", style = MaterialTheme.typography.titleMedium) }
        items(upcoming, key = { "upcoming-${it.id}" }) { item -> Text("${item.name} · ${Money.formatPaise(item.expectedAmountPaise)} · ${item.nextDueIso}") }
        if (candidates.isNotEmpty()) item { Text("Detected", style = MaterialTheme.typography.titleMedium) }
        items(candidates, key = { it.merchantKey }) { candidate -> Card { Column(Modifier.padding(12.dp)) { Text("${candidate.merchantKey} · ${Money.formatPaise(candidate.expectedAmountPaise)} · ${candidate.cadence}"); Text("Proposed due ${candidate.proposedNextDueIso}"); Row { TextButton(onClick = { name = candidate.merchantKey; amount = Money.formatPaise(candidate.expectedAmountPaise).removePrefix("₹"); due = candidate.proposedNextDueIso; cadence = candidate.cadence; merchantKey = candidate.merchantKey }) { Text("Confirm") }; TextButton(onClick = { scope.launch { database.recurringDismissedDao().insert(RecurringDismissedEntity(candidate.merchantKey, System.currentTimeMillis())) } }) { Text("Dismiss") } } } } }
        item { Text("Add recurring", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        item { OutlinedTextField(due, { due = it }, label = { Text("First due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(merchantKey, { merchantKey = it }, label = { Text("Merchant key (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item { Row { listOf(Cadences.WEEKLY, Cadences.MONTHLY, Cadences.YEARLY).forEach { c -> FilterChip(selected = cadence == c, onClick = { cadence = c }, label = { Text(c.lowercase().replaceFirstChar { it.uppercase() }) }) } } }
        item { BucketPicker(buckets, bucketId) { bucketId = it } }
        item { Button(onClick = { val parsed = Money.parseToPaise(amount); val date = runCatching { java.time.LocalDate.parse(due) }.getOrNull(); if (name.isNotBlank() && parsed != null && date != null) scope.launch { val entity = RecurringEntity(id = editingId ?: 0, name = name.trim(), merchantKey = merchantKey.trim().ifBlank { null }, expectedAmountPaise = parsed, cadence = cadence, nextDue = date.toString(), anchorDay = date.dayOfMonth, bucketId = bucketId, status = items.firstOrNull { it.id == editingId }?.status ?: RecurringStatus.ACTIVE, createdAt = items.firstOrNull { it.id == editingId }?.createdAt ?: System.currentTimeMillis()); if (editingId == null) database.recurringDao().insert(entity) else database.recurringDao().update(entity); name = ""; amount = ""; due = ""; merchantKey = ""; editingId = null } }) { Text(if (editingId == null) "Add" else "Save") } }
        item { Text("All items", style = MaterialTheme.typography.titleMedium) }
        items(items, key = { "all-${it.id}" }) { item -> Card { Column(Modifier.padding(12.dp)) { Text("${item.name} · ${Money.formatPaise(item.expectedAmountPaise)}"); Text("${item.cadence} · due ${item.nextDue}${if (RecurringMath.isStale(item.toItem(), System.currentTimeMillis())) " · Review this?" else ""}"); Row { TextButton(onClick = { scope.launch { database.recurringDao().update(item.copy(status = if (item.status == RecurringStatus.ACTIVE) RecurringStatus.PAUSED else RecurringStatus.ACTIVE)) } }) { Text(if (item.status == RecurringStatus.ACTIVE) "Pause" else "Resume") }; TextButton(onClick = { name = item.name; amount = Money.formatPaise(item.expectedAmountPaise).removePrefix("₹"); due = item.nextDue; merchantKey = item.merchantKey.orEmpty(); cadence = item.cadence; bucketId = item.bucketId; editingId = item.id }) { Text("Edit") }; TextButton(onClick = { scope.launch { database.withTransaction { val old = item.nextDue; database.recurringDao().setNextDue(item.id, RecurringMath.advance(old, item.cadence, item.anchorDay)); database.actionDao().insert(ActionEntity(kind = ActionKinds.RECURRING_SKIPPED, targetType = "recurring", targetId = item.id, description = "Skipped ${item.name} this cycle", payload = ActionPayload.encode(mapOf(PayloadKeys.OLD_NEXT_DUE to old)), createdAt = System.currentTimeMillis())) } } }) { Text("Skip cycle") }; TextButton(onClick = { cancelId = item.id }) { Text("Cancel") }; TextButton(onClick = { deleteId = item.id }) { Text("Delete") } } } } }
    }
    cancelId?.let { id -> AlertDialog(onDismissRequest = { cancelId = null }, title = { Text("Cancel recurring item?") }, text = { Text("This stops its reservation and reminders.") }, confirmButton = { TextButton(onClick = { scope.launch { database.recurringDao().getById(id)?.let { database.recurringDao().update(it.copy(status = RecurringStatus.CANCELLED)) }; cancelId = null } }) { Text("Cancel") } }, dismissButton = { TextButton(onClick = { cancelId = null }) { Text("Keep") } }) }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete recurring item?") }, text = { Text("This removes it completely, including from history views. To just stop it, use Cancel instead.") }, confirmButton = { TextButton(onClick = { scope.launch { database.recurringDao().deleteById(id); deleteId = null } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep") } }) }
}

@Composable
private fun PlanEntryAdder(onPercent: (Long) -> Unit, onFixed: (String) -> Unit) {
    var percent by remember { mutableStateOf("") }
    var fixed by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = percent, onValueChange = { percent = it }, modifier = Modifier.weight(1f), label = { Text("Percent") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Button(onClick = { percent.toLongOrNull()?.takeIf { it >= 0 }?.let { onPercent(it); percent = "" } }) { Text("Add %") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = fixed, onValueChange = { fixed = it }, modifier = Modifier.weight(1f), label = { Text("Fixed ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Button(onClick = { if (Money.parseToPaise(fixed) != null) { onFixed(fixed); fixed = "" } }) { Text("Add fixed") }
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
    val filters by viewModel.filterState.collectAsState()
    val accounts by viewModel.accounts.collectAsState(initial = emptyList()); val categories by viewModel.categories.collectAsState(initial = emptyList()); val buckets by viewModel.buckets.collectAsState(initial = emptyList()); val trips by viewModel.trips.collectAsState(initial = emptyList()); val people by viewModel.people.collectAsState(initial = emptyList())
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
        item { OutlinedTextField(filters.search, { value -> viewModel.updateFilters { it.copy(search = value) } }, label = { Text("Search transactions") }, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
        item { androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(filters.uncategorised, { viewModel.updateFilters { it.copy(uncategorised = !it.uncategorised) } }, label = { Text("Uncategorised") }); listOf("OUT", "IN").forEach { direction -> FilterChip(filters.direction == direction, { viewModel.updateFilters { it.copy(direction = if (it.direction == direction) null else direction) } }, label = { Text(direction) }) } } }
        item { FilterRow("Account", accounts.map { it.id to it.name }, filters.accountId) { id -> viewModel.updateFilters { it.copy(accountId = id) } }; FilterRow("Category", categories.map { it.id to it.name }, filters.categoryId) { id -> viewModel.updateFilters { it.copy(categoryId = id) } }; FilterRow("Bucket", buckets.map { it.id to it.name }, filters.bucketId) { id -> viewModel.updateFilters { it.copy(bucketId = id) } }; FilterRow("Trip", trips.map { it.id to it.name }, filters.tripId) { id -> viewModel.updateFilters { it.copy(tripId = id) } }; FilterRow("Person", people.map { it.id to it.name }, filters.personId) { id -> viewModel.updateFilters { it.copy(personId = id) } } }
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

@Composable private fun FilterRow(label: String, options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (options.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, modifier = Modifier.padding(top = 8.dp)); options.forEach { (id, name) -> FilterChip(selected == id, { onSelect(if (selected == id) null else id) }, label = { Text(name) }) } }
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
    val people by viewModel.people.collectAsState(initial = emptyList())
    val trips by viewModel.trips.collectAsState(initial = emptyList())
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
        if (state.direction == "OUT") item { TripPicker(trips, state.tripId) { id -> viewModel.update { copy(tripId = id) } } }
        item {
            Text("Split with people", fontWeight = FontWeight.Medium)
            Text("Your share stays in this transaction; the other shares are owed to you.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person -> FilterChip(selected = person.id in viewModel.splitPeople, onClick = { viewModel.togglePerson(person.id) }, label = { Text(person.name) }) }
            }
        }
        if (viewModel.splitPeople.isNotEmpty()) {
            item { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("EQUAL", "CUSTOM").forEachIndexed { i, mode -> SegmentedButton(selected = viewModel.splitMode == mode, onClick = { viewModel.chooseSplitMode(mode) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) } } } }
            if (viewModel.splitMode == "EQUAL") item { val amount = viewModel.validAmount(); if (amount != null) Text("${Money.formatPaise(amount)} split across you + ${viewModel.splitPeople.size}: ${SplitMath.equalShares(amount, viewModel.splitPeople.size + 1).joinToString(" / ") { Money.formatPaise(it) }}", style = MaterialTheme.typography.bodySmall) }
            if (viewModel.splitMode == "CUSTOM") {
                viewModel.splitPeople.forEach { id -> item(key = "share-$id") { val person = people.firstOrNull { it.id == id }; OutlinedTextField(value = viewModel.customShares[id].orEmpty(), onValueChange = { viewModel.setCustomShare(id, it) }, label = { Text("${person?.name ?: "Person"}'s share") }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) } }
                if (!viewModel.validSplits()) item { Text("Enter positive shares that do not exceed the total.", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (viewModel.existingSplits.isNotEmpty()) item { Text("Saved split rows", fontWeight = FontWeight.Medium) }
        viewModel.existingSplits.forEach { row -> item(key = "existing-split-${row.id}") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${people.firstOrNull { it.id == row.personId }?.name ?: "Person"} · ${Money.formatPaise(row.amountPaise)}"); TextButton(onClick = { viewModel.removeSplit(row) }) { Text("Remove") } } } }
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
                enabled = viewModel.validAmount() != null && state.accountId != null && viewModel.validDateTime() != null && viewModel.validSplits(),
            )
        }
        if (isEdit) {
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = viewModel.validAmount() != null && state.accountId != null && viewModel.validDateTime() != null && viewModel.validSplits(),
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

@Composable private fun TripPicker(trips: List<com.rajnikant.moneybrain.data.TripEntity>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }; val selected = trips.firstOrNull { it.id == selectedId }
    Column { Text("Trip", style = MaterialTheme.typography.labelLarge); Button(onClick = { expanded = true }) { Text(selected?.name ?: "No trip") }; androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) { androidx.compose.material3.DropdownMenuItem(text = { Text("No trip") }, onClick = { onSelect(null); expanded = false }); trips.forEach { trip -> androidx.compose.material3.DropdownMenuItem(text = { Text(trip.name) }, onClick = { onSelect(trip.id); expanded = false }) } } }
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
private fun SettingsScreen(onAccounts: () -> Unit, onCapture: () -> Unit, onCategoryBuckets: () -> Unit, onPeople: () -> Unit, onTrips: () -> Unit, onActivity: () -> Unit) {
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
        Spacer(Modifier.height(12.dp)); Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onPeople)) { Column(Modifier.padding(16.dp)) { Text("People", style = MaterialTheme.typography.titleMedium); Text("Balances, lending, and settlements") } }
        Spacer(Modifier.height(12.dp)); Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onTrips)) { Column(Modifier.padding(16.dp)) { Text("Trips", style = MaterialTheme.typography.titleMedium); Text("Start and stop spending trips") } }
        Spacer(Modifier.height(12.dp)); Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onActivity)) { Column(Modifier.padding(16.dp)) { Text("Activity", style = MaterialTheme.typography.titleMedium); Text("Review automatic actions and undo them") } }
    }
}

@Composable private fun PeopleScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, onBack: () -> Unit, onPerson: (Long) -> Unit) {
    val people by database.personDao().observeAll().collectAsState(initial = emptyList()); val balances by database.personLedgerDao().observeBalances().collectAsState(initial = emptyList()); val scope = rememberCoroutineScope(); var name by remember { mutableStateOf("") }; val map = balances.associate { it.personId to it.balance }; val summary = peopleSummary(balances)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { TextButton(onClick = onBack) { Text("Back") }; Text("People", style = MaterialTheme.typography.headlineSmall); Text("Owed to you ${Money.formatPaise(summary.owedToYou)} · You owe ${Money.formatPaise(summary.youOwe)}") }; item { Row { OutlinedTextField(name, { name = it }, label = { Text("New person") }, modifier = Modifier.weight(1f)); Button(onClick = { if (name.isNotBlank()) scope.launch { database.personDao().insert(com.rajnikant.moneybrain.data.PersonEntity(name = name.trim(), createdAt = System.currentTimeMillis())); name = "" } }) { Text("Add") } } }; items(people, key = { it.id }) { person -> val balance = map[person.id] ?: 0; Card(Modifier.fillMaxWidth().clickable { onPerson(person.id) }) { Column(Modifier.padding(12.dp)) { Text(person.name); Text(if (balance > 0) "owes you ${Money.formatPaise(balance)}" else if (balance < 0) "you owe ${Money.formatPaise(-balance)}" else "settled") } } } }
}

@Composable private fun PersonDetailScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, personId: Long, onBack: () -> Unit) {
    val people by database.personDao().observeAll().collectAsState(initial = emptyList()); val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList()); val rows by database.personLedgerDao().observeForPerson(personId).collectAsState(initial = emptyList()); val scope = rememberCoroutineScope(); val person = people.firstOrNull { it.id == personId } ?: return; val balance = SplitMath.balance(rows.map { it.amountPaise }); var lent by remember { mutableStateOf("") }; var owed by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var accountId by remember { mutableStateOf<Long?>(null) }; var settleConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(accounts) { if (accountId == null) accountId = accounts.firstOrNull { it.type == "CASH" }?.id ?: accounts.firstOrNull()?.id }
    fun recordLent() { val amount = Money.parseToPaise(lent)?.takeIf { it > 0 } ?: return; val account = accountId ?: return; scope.launch { database.withTransaction { val now = System.currentTimeMillis(); val tx = database.transactionDao().insert(TransactionEntity(amountPaise = amount, direction = "OUT", accountId = account, categoryId = null, merchant = person.name, occurredAt = now, notes = "Lent to ${person.name}", source = "MANUAL", createdAt = now)); database.personLedgerDao().insert(PersonLedgerEntity(personId = personId, amountPaise = amount, kind = LedgerKinds.LENT, transactionId = tx, note = null, createdAt = now)) }; lent = "" } }
    fun recordOwed() { val amount = Money.parseToPaise(owed)?.takeIf { it > 0 } ?: return; scope.launch { database.personLedgerDao().insert(PersonLedgerEntity(personId = personId, amountPaise = -amount, kind = LedgerKinds.I_OWE, transactionId = null, note = note.trim().ifBlank { null }, createdAt = System.currentTimeMillis())); owed = ""; note = "" } }
    if (settleConfirm) AlertDialog(onDismissRequest = { settleConfirm = false }, title = { Text("Settle up?") }, text = { Text(if (balance > 0) "${person.name} pays you ${Money.formatPaise(balance)}" else "You pay ${person.name} ${Money.formatPaise(-balance)}") }, confirmButton = { TextButton(onClick = { val account = accountId ?: return@TextButton; scope.launch { database.withTransaction { val now = System.currentTimeMillis(); val amount = kotlin.math.abs(balance); val tx = database.transactionDao().insert(TransactionEntity(amountPaise = amount, direction = if (balance > 0) "IN" else "OUT", accountId = account, categoryId = null, merchant = person.name, occurredAt = now, notes = "Settlement with ${person.name}", source = "MANUAL", createdAt = now)); database.personLedgerDao().insert(PersonLedgerEntity(personId = personId, amountPaise = SplitMath.settlementAmount(balance), kind = LedgerKinds.SETTLEMENT, transactionId = tx, note = null, createdAt = now)) }; settleConfirm = false } }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { settleConfirm = false }) { Text("Cancel") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { TextButton(onClick = onBack) { Text("Back") }; Text(person.name, style = MaterialTheme.typography.headlineSmall); Text(if (balance > 0) "owes you ${Money.formatPaise(balance)}" else if (balance < 0) "you owe ${Money.formatPaise(-balance)}" else "settled") }; item { AccountPicker(accounts, accountId) { accountId = it } }; item { OutlinedTextField(lent, { lent = it }, label = { Text("I lent them") }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Button(onClick = ::recordLent, enabled = Money.parseToPaise(lent)?.let { it > 0 } == true && accountId != null) { Text("Record loan") } }; item { OutlinedTextField(owed, { owed = it }, label = { Text("They paid for me") }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth()); Button(onClick = ::recordOwed, enabled = Money.parseToPaise(owed)?.let { it > 0 } == true) { Text("Record") } }; if (balance != 0L) item { Button(onClick = { settleConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Settle up") } }; item { Text("History", fontWeight = FontWeight.Medium) }; items(rows, key = { it.id }) { row -> Card { Column(Modifier.padding(12.dp)) { Text("${row.kind.lowercase().replaceFirstChar { it.uppercase() }} · ${Money.formatPaise(kotlin.math.abs(row.amountPaise))}"); Text(row.note ?: row.transactionId?.let { "Linked transaction" } ?: "No account transaction", style = MaterialTheme.typography.bodySmall); Text(Instant.ofEpochMilli(row.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.bodySmall) } } } }
}

@Composable private fun TripsScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, onBack: () -> Unit, onTrip: (Long) -> Unit) {
    val trips by database.tripDao().observeAll().collectAsState(initial = emptyList()); val scope = rememberCoroutineScope(); val snackbar = remember { SnackbarHostState() }; var name by remember { mutableStateOf("") }; var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }; var deleteTrip by remember { mutableStateOf<Long?>(null) }; val active = trips.firstOrNull { it.endedAt == null }
    deleteTrip?.let { id -> AlertDialog(onDismissRequest = { deleteTrip = null }, title = { Text("Delete trip?") }, text = { Text("Its transactions will stay, but will no longer belong to this trip.") }, confirmButton = { TextButton(onClick = { scope.launch { database.withTransaction { database.transactionDao().clearTrip(id); database.tripDao().deleteById(id) }; deleteTrip = null } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteTrip = null }) { Text("Keep") } }) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { TextButton(onClick = onBack) { Text("Back") }; Text("Trips", style = MaterialTheme.typography.headlineSmall) }; if (active != null) item { Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Active: ${active.name}"); Button(onClick = { scope.launch { database.tripDao().stop(active.id, System.currentTimeMillis()) } }) { Text("Stop trip") } } } }; item { OutlinedTextField(name, { name = it }, label = { Text("Trip name") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { if (active != null) scope.launch { snackbar.showSnackbar("Stop ${active.name} before starting another trip") } else if (name.isNotBlank()) scope.launch { val now = System.currentTimeMillis(); database.tripDao().insert(com.rajnikant.moneybrain.data.TripEntity(name = name.trim(), startedAt = now, endedAt = null, createdAt = now)); name = "" } }, enabled = name.isNotBlank()) { Text("Start now") } }; item { Text("Or create a dated trip", fontWeight = FontWeight.Medium); OutlinedTextField(start, { start = it }, label = { Text("Start (YYYY-MM-DD HH:MM)") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(end, { end = it }, label = { Text("End (YYYY-MM-DD HH:MM)") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { val s = parseTripDate(start); val e = parseTripDate(end); if (name.isNotBlank() && s != null && e != null && e >= s) scope.launch { database.tripDao().insert(com.rajnikant.moneybrain.data.TripEntity(name = name.trim(), startedAt = s, endedAt = e, createdAt = System.currentTimeMillis())); name = ""; start = ""; end = "" } }, enabled = name.isNotBlank() && parseTripDate(start) != null && parseTripDate(end) != null) { Text("Create dated trip") } }; items(trips, key = { it.id }) { trip -> Card(Modifier.fillMaxWidth().clickable { onTrip(trip.id) }) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${trip.name} · ${if (trip.endedAt == null) "Active" else "Ended"}"); if (trip.endedAt != null) TextButton(onClick = { deleteTrip = trip.id }) { Text("Delete") } } } } } } }
@Composable private fun TripDetailScreen(database: com.rajnikant.moneybrain.data.MoneyBrainDatabase, tripId: Long, onBack: () -> Unit) {
    val trips by database.tripDao().observeAll().collectAsState(initial = emptyList()); val transactions by database.transactionDao().observeAll().collectAsState(initial = emptyList()); val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList()); val ledger by database.personLedgerDao().observeAll().collectAsState(initial = emptyList()); val trip = trips.firstOrNull { it.id == tripId } ?: return; val spend = transactions.filter { it.tripId == tripId && it.direction == "OUT" }; val transactionIds = spend.map { it.id }.toSet(); val total = tripTotal(transactions, tripId); val owed = ledger.filter { it.kind == LedgerKinds.SPLIT && it.transactionId in transactionIds }.sumOf { it.amountPaise }; val names = categories.associate { it.id to it.name }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { TextButton(onClick = onBack) { Text("Back") }; Text(trip.name, style = MaterialTheme.typography.headlineSmall); Text("Total ${Money.formatPaise(total)}"); Text("Owed to you within this trip ${Money.formatPaise(owed)}") }; item { Text("By category", fontWeight = FontWeight.Medium) }; items(spend.groupBy { names[it.categoryId] ?: "Uncategorised" }.toList(), key = { it.first }) { (category, rows) -> Text("$category · ${Money.formatPaise(rows.sumOf { it.amountPaise })}") }; item { Text("By day", fontWeight = FontWeight.Medium) }; items(spend.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.toList(), key = { it.first }) { (day, rows) -> Text("$day · ${Money.formatPaise(rows.sumOf { it.amountPaise })}") }; item { Text("Transactions", fontWeight = FontWeight.Medium) }; items(spend, key = { it.id }) { tx -> Card { Column(Modifier.padding(12.dp)) { Text(tx.merchant ?: "Expense"); Text(Money.formatPaise(tx.amountPaise)); Text(Instant.ofEpochMilli(tx.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.bodySmall) } } } }
}

private fun parseTripDate(text: String): Long? = runCatching { java.time.LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

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
