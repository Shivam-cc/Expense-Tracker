package com.expensetracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.mobile.data.*
import com.expensetracker.mobile.sync.FailedOutboxItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ExpenseApp() } }
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
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("ExpenseFlow", style = MaterialTheme.typography.headlineMedium)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!ready) {
                CircularProgressIndicator()
            } else if (session == null) {
                AuthScreen(vm, busy)
            } else {
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
private fun AuthScreen(vm: ExpenseViewModel, busy: Boolean) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (register) "Create account" else "Sign in", style = MaterialTheme.typography.titleLarge)
        Text("Expired sessions keep your offline data. Sign in with the same account to continue syncing.")
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        if (register) {
            OutlinedButton(onClick = { vm.sendOtp(name, email) }, enabled = !busy) { Text("Send email OTP") }
            OutlinedTextField(otp, { otp = it }, label = { Text("OTP") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Button(onClick = { vm.authenticate(Credentials(name, email, password, otp, register)) }, enabled = !busy) {
            Text(if (register) "Create account" else "Sign in")
        }
        TextButton(onClick = { register = !register }, enabled = !busy) { Text(if (register) "Already have an account" else "Sign up") }
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
    val formatted = last?.let {
        DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()))
    } ?: "Never"
    Text("Last synced: $formatted")
    Text("Unsynced: $unsynced${if (syncing) " - Syncing..." else ""}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::sync, enabled = !busy && !syncing) { Text("Sync now") }
        TextButton(onClick = { vm.signOut() }, enabled = !busy) { Text("Sign out") }
    }
    ScrollableTabRow(selectedTabIndex = tab) {
        listOf("Expenses", "Categories", "Failed (${failed.size})").forEachIndexed { index, title ->
            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
        }
    }
    when (tab) {
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
