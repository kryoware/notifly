@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ph.notifly.domain.model.*
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.hint
import ph.notifly.ui.theme.accents

@Composable
fun TransactionRow(transaction: Transaction, open: () -> Unit) {
    val color = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.accents.income
        TransactionType.EXPENSE -> MaterialTheme.accents.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (transaction.type) {
        TransactionType.INCOME -> Icons.Default.ArrowDownward
        TransactionType.EXPENSE -> Icons.Default.ArrowUpward
        TransactionType.TRANSFER -> Icons.Default.SwapHoriz
    }
    TextButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Icon(icon, contentDescription = transaction.type.name, tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.title, color = MaterialTheme.colorScheme.onSurface)
            Text(if (transaction.status == TransactionStatus.NEEDS_REVIEW) "Needs review" else transaction.category,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((if (transaction.type == TransactionType.INCOME) "+" else if (transaction.type == TransactionType.EXPENSE) "−" else "") + money(transaction.amountMinor), color = color)
    }
}

@Composable
fun HomeScreen(model: HomeModel) {
    val s by model.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Confirmed balance", style = MaterialTheme.typography.labelLarge)
                    Text(money(s.net), style = MaterialTheme.typography.headlineLarge)
                    Text("Transfers and transactions awaiting review are excluded.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val pending = s.rows.count { it.status == TransactionStatus.NEEDS_REVIEW }
        if (pending > 0) item { OutlinedButton(onClick = { model.navigate("transactions") }) { Text("$pending transactions need review") } }
        item { Text("Recent", style = MaterialTheme.typography.titleLarge) }
        items(s.rows.take(5), key = { it.id }) { TransactionRow(it) { model.navigate("edit/${it.id}") } }
        if (s.rows.isEmpty()) item { Text("No transactions yet. Add one manually to get started.") }
        item { Button(onClick = { model.navigate("edit/0") }) { Text("Add transaction") } }
    }
}

@Composable
fun TransactionsScreen(model: TransactionsModel) {
    val s by model.state.collectAsState()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { model.navigate("edit/0") }) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionFilter.entries.forEach { f ->
                FilterChip(s.filter == f, { model.filter(f) }, label = { Text(when (f) {
                    TransactionFilter.ALL -> "All"; TransactionFilter.NEEDS_REVIEW -> "Needs review"
                    TransactionFilter.INCOME -> "Income"; TransactionFilter.EXPENSE -> "Expense"
                }) })
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(s.rows, key = { it.id }) { TransactionRow(it) { model.navigate("edit/${it.id}") } }
            if (s.rows.isEmpty()) item { Text("Nothing here yet", Modifier.padding(24.dp)) }
        }
}
}

}

@Composable
fun InsightsScreen(model: InsightsModel) {
    val s by model.state.collectAsState()
    val expenses = s.rows.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }.entries.sortedByDescending { it.value }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Spending by category", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Confirmed transactions only. Transfers are excluded.") }
        items(expenses) { (category, amount) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, Modifier.weight(1f)); Text(money(amount))
            }
        }
        if (expenses.isEmpty()) item { Text("No confirmed expenses yet.") }
        item { Text("Income: ${money(s.rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor })}") }
        item { Text("Expenses: ${money(expenses.sumOf { it.value })}") }
        item { Text("Balance: ${money(s.net)}") }
    }
}

@Composable
fun EditorScreen(model: EditorModel) {
    val s by model.state.collectAsState()
    var delete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        s.sourceText?.let { text -> item { Text("Source notification (device only): $text") } }
        if (s.original?.status == TransactionStatus.NEEDS_REVIEW) item { Text("Parsed on your device. Check the details before confirming.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = s.type == type,
                            onClick = { model.edit(type = type) },
                            shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                            label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
        } }
        item { OutlinedTextField(s.title, { model.edit(title = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text("Amount (PHP)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        item { OutlinedTextField(s.category, { model.edit(category = it) }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(s.date, { model.edit(date = it) }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("Source: ${s.original?.sourceApp ?: s.sourceApp ?: "Manual"}") }
        if (s.original != null) item { Text("Date: ${s.original!!.occurredAt}") }
        s.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        item { Button(onClick = model::save, enabled = s.ready && !s.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.original?.status == TransactionStatus.NEEDS_REVIEW) "Confirm transaction" else "Save transaction")
        } }
        if (s.original != null) item { TextButton(onClick = { delete = true }) { Text("Delete transaction") } }
        item { TextButton(onClick = { model.navigate("transactions") }) { Text("Cancel") } }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete transaction?") },
        text = { Text("You can undo this immediately after deleting.") },
        confirmButton = { TextButton(onClick = { delete = false; model.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}

@Composable
fun SettingsScreen(model: SettingsModel, permissionAvailable: Boolean, requestPermission: () -> Unit) {
    val s by model.state.collectAsState()
    val source = org.koin.compose.koinInject<ph.notifly.domain.source.TransactionSource>()
    val connection by source.connection.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("On-device capture", style = MaterialTheme.typography.titleLarge) }
        item { Text(if (permissionAvailable) "Notification access enabled" else "Notification access disabled — manual entry still works") }
        item { Text("Listener: $connection") }
        item { OutlinedButton(onClick = requestPermission) { Text("Manage notification access") } }
        item { TextButton(onClick = { model.navigate("allow-list") }) { Text("Allowed apps") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Offline mode", Modifier.weight(1f)); Switch(s.offline, model::offline, modifier = Modifier.semantics { contentDescription = "Offline mode" }) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Send crash reports", Modifier.weight(1f)); Switch(s.crashReporting, model::crashReporting, modifier = Modifier.semantics { contentDescription = "Send crash reports" }) } }
        item { Text("Reports contain stack traces and device info only — never notification text.", style = MaterialTheme.typography.bodySmall) }
        item { Text("Theme", style = MaterialTheme.typography.titleLarge) }
        item { Text("${s.pending} changes waiting to sync. Cloud sync is not configured.") }
        item {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = "${s.palette.name} · ${s.palette.hint}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    NotiflyPalette.entries.forEach { palette ->
                        DropdownMenuItem(
                            text = { Text("${palette.name} · ${palette.hint}") },
                            onClick = { expanded = false; model.palette(palette) },
                        )
                    }
                }
            }
        }
        item { TextButton(onClick = { model.navigate("themes") }) { Text("Inspect theme palettes") } }
        item { TextButton(onClick = { model.navigate("log") }) { Text("Notification log") } }
        item { TextButton(onClick = { model.navigate("auth") }) { Text("Account / sign in") } }
    }
}

@Composable
fun AllowListScreen(model: AllowListModel, onboarding: Boolean = false) {
    val s by model.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Text("Only apps you explicitly enable can create captures.") }
        items(s.apps, key = { it.packageName }) { app ->
            ListItem(headlineContent = { Text(app.label) }, supportingContent = { Text("${app.kind} · ${app.capturedCount} captures") },
                trailingContent = { Switch(app.listening, { model.toggle(app) }, modifier = Modifier.semantics { contentDescription = "Listen to ${app.label}" }) })
        }
        if (s.apps.isEmpty()) item { Text("No installed apps available.", Modifier.padding(vertical = 24.dp)) }
        if (onboarding) item { Button(onClick = { model.navigate("auth") }) { Text("Continue") } }
    }
}

@Composable
fun OnboardingScreen(
    model: OnboardingModel,
    requestPermission: () -> Unit,
    permissionAvailable: Boolean,
    batteryExempt: Boolean,
    requestBatteryExemption: () -> Unit,
) {
    val s by model.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(listOf("Stop typing your expenses", "How it works", "One permission to grant", "Keep it running")[s.page], style = MaterialTheme.typography.headlineLarge)
        Text(listOf("Track payments from the apps you choose. Your notifications stay on your device.",
            "Choose your apps. We parse payment alerts on-device. You review and confirm every transaction.",
            "Notification access lets Notifly read alerts only from allowed apps. Raw notification text is never uploaded.",
            "Android can pause background apps to save power, and some phones do it aggressively. Turning that off for Notifly keeps captures arriving promptly.")[s.page])
        Spacer(Modifier.weight(1f))
        Text("${s.page + 1} of 4")
        when (s.page) {
            0, 1 -> Button(onClick = model::next) { Text(if (s.page == 0) "Get started" else "Next") }
            2 -> {
                Text(if (permissionAvailable) "Access enabled" else "Access not enabled. You can continue manually.")
                Button(onClick = requestPermission) { Text("Grant access") }
                OutlinedButton(onClick = model::next) { Text(if (permissionAvailable) "Continue" else "Skip — add manually") }
            }
            else -> {
                Text(if (batteryExempt) "Battery optimisation is off for Notifly" else "Notifly is still battery-optimised. Capture still works — it may just be delayed on some phones.")
                if (!batteryExempt) Text("In the list that opens, switch the filter to All apps, then pick Notifly.")
                Button(onClick = requestBatteryExemption) { Text("Open battery settings") }
                OutlinedButton(onClick = { model.navigate("choose-apps") }) { Text(if (batteryExempt) "Continue" else "Skip for now") }
            }
        }
        TextButton(onClick = { model.navigate("auth") }) { Text("I already have an account") }
    }
}

@Composable
fun AuthScreen(model: AuthModel, demo: Boolean) {
    val s by model.state.collectAsState()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (s.signup) "Create account" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
        if (demo) Text("Demo account flow — no account will be created.")
        OutlinedTextField(s.email, { model.edit(email = it) }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(s.password, { model.edit(password = it) }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { model.submit() }) { Text(if (s.signup) "Create account" else "Sign in") }
        TextButton(onClick = { model.edit(signup = !s.signup) }) { Text(if (s.signup) "I already have an account" else "Create account") }
        OutlinedButton(onClick = { model.startOffline() }) { Text("Continue offline") }
    }
}
