package com.expensetracker.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.mobile.data.*
import com.expensetracker.mobile.sync.FailedOutboxItem
import com.expensetracker.mobile.util.Money
import java.time.LocalDate
import kotlinx.datetime.todayIn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        setContent { ExpenseFlowTheme { ExpenseApp() } }
    }
}

@Composable
private fun ExpenseApp(vm: ExpenseViewModel = viewModel()) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val session by vm.graph.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.graph.message.collectAsStateWithLifecycle()
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it, duration = SnackbarDuration.Long)
            vm.graph.message.value = null
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        when {
            !ready -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session == null -> AuthScreen(vm, busy, Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                Text("ExpenseFlow", style = MaterialTheme.typography.headlineMedium)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                SignedInScreen(vm, busy, session!!.currency ?: "INR")
            }
        }
    }
    prompt?.let { current ->
        AlertDialog(
            onDismissRequest = { if (!busy) vm.prompt.value = null },
            title = { Text("Discard unsynced changes?") },
            text = { Text("${current.count} changes have not synced. Signing out will permanently discard them. If more arrive, you will be asked again.") },
            confirmButton = { TextButton(onClick = vm::confirmDiscard, enabled = !busy) { Text("Discard ${current.count}") } },
            dismissButton = { TextButton(onClick = { vm.prompt.value = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SignedInScreen(vm: ExpenseViewModel, busy: Boolean, currency: String) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val unsynced by vm.unsynced.collectAsStateWithLifecycle()
    val last by vm.lastSynced.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val syncing by vm.graph.syncing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseRecord?>(null) }
    var deleting by remember { mutableStateOf<ExpenseRecord?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val total = monthTotalMinor(
        expenses.map { SpendEntry(it.amountMinor, it.currency, it.date) },
        currency, kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()),
    )
    LaunchedEffect(failed.size) { if (failed.isEmpty() && tab == 2) tab = 0 }
    val showFailed = failed.isNotEmpty()
    val activeTab = if (tab == 2 && !showFailed) 0 else tab
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge)
            Text("${Money.formatMinor(total, Money.minorDigits(currency))} $currency", style = MaterialTheme.typography.headlineMedium)
        }
    }
    val syncLabel = when {
        syncing -> "Syncing…"
        unsynced > 0 -> "$unsynced pending"
        last != null -> "Synced ${relativeTime(last!!.toEpochMilliseconds())}"
        else -> "Not synced yet"
    }
    AssistChip(
        onClick = vm::sync,
        enabled = !busy && !syncing,
        label = { Text(syncLabel, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.testTag("sync-status"),
    )
    ScrollableTabRow(selectedTabIndex = activeTab) {
        val titles = buildList { add("Expenses"); add("Categories"); if (showFailed) add("Failed (${failed.size})") }
        titles.forEachIndexed { index, title ->
            Tab(selected = activeTab == index, onClick = { tab = index }, text = { Text(title) })
        }
    }
    when (activeTab) {
        0 -> {
            Button(onClick = { editing = null; editorOpen = true }, enabled = !busy) { Text("Add expense") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (expenses.isEmpty()) item { Text("No expenses yet. Add one, even offline.") }
                items(expenses, key = { it.id }) { expense ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(expense.description, style = MaterialTheme.typography.titleMedium)
                            Text("${expense.amount} ${expense.currency} - ${expense.date}")
                            val category = categories.find { it.id == expense.categoryClientId || (expense.categoryServerId != null && it.serverId == expense.categoryServerId) }
                            Text(category?.name ?: "Uncategorized")
                            Row {
                                TextButton(onClick = { editing = expense; editorOpen = true }, enabled = !busy) { Text("Edit") }
                                TextButton(onClick = { deleting = expense }, enabled = !busy) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        1 -> CategoryScreen(vm, categories, busy, selectedCategory) { selectedCategory = it }
        2 -> FailedScreen(vm, failed, busy)
    }
    if (editorOpen) ExpenseEditor(vm, editing, categories, currency, busy, selectedCategory) { editorOpen = false }
    deleting?.let { expense ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete expense?") },
            text = { Text(expense.description) },
            confirmButton = { TextButton(onClick = { vm.delete(expense); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ExpenseEditor(vm: ExpenseViewModel, existing: ExpenseRecord?, categories: List<CategoryRecord>, defaultCurrency: String, busy: Boolean, selectedCategory: String?, close: () -> Unit) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var date by remember { mutableStateOf(existing?.date?.toString() ?: LocalDate.now().toString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var category by remember {
        mutableStateOf(if (existing == null) selectedCategory else existing.categoryClientId
            ?: categories.find { existing.categoryServerId != null && it.serverId == existing.categoryServerId }?.id)
    }
    var picker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(if (existing == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(currency, { currency = it }, label = { Text("Currency (ISO code)") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Date (yyyy-MM-dd)") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { picker = true }) { Text(categories.find { it.id == category }?.name ?: "Choose category") }
                    DropdownMenu(picker, { picker = false }) {
                        DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { category = null; picker = false })
                        categories.forEach { row ->
                            DropdownMenuItem(text = { Text(row.name) }, onClick = { category = row.id; picker = false })
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveExpense(existing, description, amount, currency, date, category, notes, close) }, enabled = !busy) { Text("Save") } },
        dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryScreen(vm: ExpenseViewModel, categories: List<CategoryRecord>, busy: Boolean, selected: String?, select: (String) -> Unit) {
    var editing by remember { mutableStateOf<CategoryRecord?>(null) }
    var open by remember { mutableStateOf(false) }
    Button(onClick = { editing = null; open = true }, enabled = !busy) { Text("Create category") }
    selected?.let { Text("Selected: ${categories.find { row -> row.id == it }?.name ?: it}") }
    LazyColumn(Modifier.testTag("categories")) {
        items(categories, key = { it.id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { select(row.id) }) { Text(row.name) }
                if (!row.isGlobal) TextButton(onClick = { editing = row; open = true }, enabled = !busy) { Text("Rename") }
            }
        }
    }
    if (open) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var check by remember { mutableStateOf<CategoryNameCheck?>(null) }
        AlertDialog(onDismissRequest = { open = false },
            title = { Text(if (editing == null) "Create category" else "Rename category") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it; check = null }, label = { Text("Category name") })
                    if (check == CategoryNameCheck.Blank) Text("Enter a category name")
                    (check as? CategoryNameCheck.AlreadyExists)?.let { duplicate ->
                        Text("\"${duplicate.name}\" already exists.")
                        val existingCategory = duplicate.existing ?: categories.find { it.name.equals(duplicate.name, ignoreCase = true) }
                        if (existingCategory != null) {
                            TextButton(onClick = { select(existingCategory.id); open = false }) { Text("Select ${duplicate.name}") }
                        } else {
                            Text("Sync to download this built-in category, then select it.")
                            TextButton(onClick = vm::sync, enabled = !busy) { Text("Sync categories") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveCategory(editing, name) { result -> check = result; if (result is CategoryNameCheck.Valid) open = false } }, enabled = !busy) { Text("Save") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}

@Composable
private fun FailedScreen(vm: ExpenseViewModel, failed: List<FailedOutboxItem>, busy: Boolean) {
    var discard by remember { mutableStateOf<FailedOutboxItem?>(null) }
    LazyColumn {
        if (failed.isEmpty()) item { Text("No failed items", Modifier.padding(12.dp)) }
        items(failed, key = { "${it.entity}:${it.clientId}" }) { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${item.entity} ${item.operation}")
                    Text(item.clientId, style = MaterialTheme.typography.bodySmall)
                    Text(item.error ?: "The server rejected this change")
                    Row {
                        TextButton(onClick = { vm.retry(item) }, enabled = !busy) { Text("Retry") }
                        TextButton(onClick = { discard = item }, enabled = !busy) { Text("Discard") }
                    }
                }
            }
        }
    }
    discard?.let { item ->
        AlertDialog(onDismissRequest = { discard = null }, title = { Text("Discard failed change?") },
            text = { Text("The local change will be removed. Any existing server copy is restored on the next successful sync.") },
            confirmButton = { TextButton(onClick = { vm.discard(item); discard = null }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discard = null }) { Text("Cancel") } })
    }
}

private fun relativeTime(thenMs: Long): String {
    val mins = (System.currentTimeMillis() - thenMs).coerceAtLeast(0) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1_440 -> "${mins / 60}h ago"
        else -> "${mins / 1_440}d ago"
    }
}