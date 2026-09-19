package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ph.notifly.domain.model.*
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.accents

@Composable
fun TransactionRow(transaction: Transaction, open: () -> Unit) {
    val color = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.accents.income
        TransactionType.EXPENSE -> MaterialTheme.accents.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TextButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
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
    Column(Modifier.fillMaxSize()) {
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
        Button(onClick = { model.navigate("edit/0") }, Modifier.padding(16.dp)) { Text("Add transaction") }
    }
}

@Composable
fun EditorScreen(model: EditorModel) {
    val s by model.state.collectAsState()
    var delete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.original?.status == TransactionStatus.NEEDS_REVIEW) item { Text("Parsed on your device. Check the details before confirming.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionType.entries.forEach { type -> FilterChip(s.type == type, { model.edit(type = type) }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        } }
        item { OutlinedTextField(s.title, { model.edit(title = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text("Amount (PHP)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
        item { OutlinedTextField(s.category, { model.edit(category = it) }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("Source: ${s.original?.sourceApp ?: "Manual"}") }
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("On-device capture", style = MaterialTheme.typography.titleLarge) }
        item { Text(if (permissionAvailable) "Notification access enabled" else "Notification access disabled — manual entry still works") }
        item { OutlinedButton(onClick = requestPermission) { Text("Manage notification access") } }
        item { TextButton(onClick = { model.navigate("allow-list") }) { Text("Allowed apps") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Offline mode"); Switch(s.offline, model::offline) } }
        item { Text("Theme", style = MaterialTheme.typography.titleLarge) }
        items(NotiflyPalette.entries) { p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.name); RadioButton(s.palette == p, { model.palette(p) })
        } }
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
                trailingContent = { Switch(app.listening, { model.toggle(app) }) })
        }
        if (s.apps.isEmpty()) item { Text("No installed apps available.", Modifier.padding(vertical = 24.dp)) }
        if (onboarding) item { Button(onClick = { model.navigate("auth") }) { Text("Continue") } }
    }
}

@Composable
fun OnboardingScreen(model: OnboardingModel, requestPermission: () -> Unit, permissionAvailable: Boolean) {
    val s by model.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(listOf("Stop typing your expenses", "How it works", "One permission to grant")[s.page], style = MaterialTheme.typography.headlineLarge)
        Text(listOf("Track payments from the apps you choose. Your notifications stay on your device.",
            "Choose your apps. We parse payment alerts on-device. You review and confirm every transaction.",
            "Notification access lets Notifly read alerts only from allowed apps. Raw notification text is never uploaded.")[s.page])
        Spacer(Modifier.weight(1f))
        Text("${s.page + 1} of 3")
        if (s.page < 2) Button(onClick = model::next) { Text(if (s.page == 0) "Get started" else "Next") }
        else {
            Text(if (permissionAvailable) "Access enabled" else "Access not enabled. You can continue manually.")
            Button(onClick = requestPermission) { Text("Grant access") }
            OutlinedButton(onClick = { model.navigate("choose-apps") }) { Text(if (permissionAvailable) "Continue" else "Skip — add manually") }
        }
        TextButton(onClick = { model.navigate("auth") }) { Text("I already have an account") }
    }
}

@Composable
fun AuthScreen(model: AuthModel, demo: Boolean) {
    val s by model.state.collectAsState()
    Column(Modifier.fillMaxSize().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
