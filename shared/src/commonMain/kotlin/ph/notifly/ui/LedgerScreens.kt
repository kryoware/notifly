package ph.notifly.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import kotlin.time.Clock

class LedgerSettingsModel(private val ledger: LedgerRepository, apps: AllowListRepository) : ScreenModel() {
    data class State(val accounts: List<Account> = emptyList(), val categories: List<Category> = emptyList(),
        val apps: List<AllowedApp> = emptyList(), val drafts: List<CapturedDraft> = emptyList(), val loaded: Boolean = false)
    val state = combine(ledger.observeAccounts(), ledger.observeCategories(), apps.observeAll(), ledger.observeDrafts()) { a, c, apps, drafts ->
        State(a, c, apps, drafts, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), State())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    fun save(account: Account) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        work { try { ledger.saveAccount(account); navigate("accounts") } finally { mutableBusy.value = false } }
    }
    fun save(category: Category) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        work { try { ledger.saveCategory(category); navigate("categories") } finally { mutableBusy.value = false } }
    }
    fun deleteAccount(id: Long) = work { ledger.deleteAccount(id); navigate("accounts") }
    fun discardDraft(id: Long) = work { ledger.deleteDraft(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceField(title: String, value: String, options: List<T>,
    error: String? = null, label: (T) -> String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(title) }, isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { item -> DropdownMenuItem(text = { Text(label(item)) }, onClick = { choose(item); expanded = false }) }
        }
    }
}

@Composable
internal fun AccountPicker(title: String, selected: Long?, accounts: List<Account>, error: String? = null, choose: (Long) -> Unit) {
    ChoiceField(title, accounts.find { it.id == selected }?.name ?: "Choose an account", accounts, error,
        label = { it.name + if (it.type == AccountType.CARD) " · Card" else "" }) { choose(it.id) }
}

@Composable
fun AccountsScreen(model: LedgerSettingsModel) {
    val s by model.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { model.navigate("account/0") }, Modifier.fillMaxWidth()) { Text("Add account") } }
        if (s.loaded && s.accounts.isEmpty()) item { Text("Add a bank, card or wallet account before creating transactions.") }
        items(s.accounts, key = { it.id }) { account ->
            SettingsRow(account.name, account.type.name.lowercase().replaceFirstChar { it.uppercase() } +
                (account.lastFour?.let { " · Ending $it" } ?: "") + if (account.archived) " · Archived" else "",
                onClick = { model.navigate("account/${account.id}") })
        }
    }
}

internal fun balanceInput(text: String): Long? {
    val clean = text.trim()
    val magnitude = clean.removePrefix("-")
    val minor = parseAmountMinor(magnitude) ?: 0L.takeIf { Regex("0+(\\.0{1,2})?").matches(magnitude) } ?: return null
    return if (clean.startsWith("-")) -minor else minor
}

@Composable
fun AccountEditorScreen(model: LedgerSettingsModel, id: Long) {
    val s by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    if (!s.loaded) { CircularProgressIndicator(); return }
    val original = s.accounts.find { it.id == id }
    if (id != 0L && original == null) { Text("Account no longer exists."); return }
    var name by remember(id) { mutableStateOf(original?.name.orEmpty()) }
    var type by remember(id) { mutableStateOf(original?.type ?: AccountType.WALLET) }
    var cardType by remember(id) { mutableStateOf(original?.cardType.orEmpty()) }
    var lastFour by remember(id) { mutableStateOf(original?.lastFour.orEmpty()) }
    var free by remember(id) { mutableStateOf(original?.freeTransfer ?: false) }
    var amount by remember(id) { mutableStateOf(amountText(original?.balanceMinor ?: 0)) }
    var due by remember(id) { mutableStateOf(original?.dueDate?.toString().orEmpty()) }
    var statement by remember(id) { mutableStateOf(original?.statementDate?.toString().orEmpty()) }
    var archived by remember(id) { mutableStateOf(original?.archived ?: false) }
    var links by remember(id) { mutableStateOf(original?.linkedApps ?: emptySet()) }
    var reconcile by remember(id) { mutableStateOf(original == null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { ChoiceField("Type", type.name.lowercase().replaceFirstChar { it.uppercase() }, AccountType.entries,
            label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { type = it } }
        item { OutlinedTextField(lastFour, { lastFour = it }, label = { Text("Last four digits (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (type == AccountType.CARD) {
            item { OutlinedTextField(cardType, { cardType = it }, label = { Text("Card network (optional)") },
                placeholder = { Text("Visa, Mastercard…") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(due, { due = it }, label = { Text("Monthly due day (1–31, optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(statement, { statement = it }, label = { Text("Monthly statement day (1–31, optional)") },
                supportingText = { Text("Days beyond the end of a month use its last day.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        }
        item { MoneyField(amount, { amount = it; reconcile = true },
            label = { Text(if (type == AccountType.CARD) "Debt owed (PHP)" else "Balance (PHP)") },
            supportingText = { Text(if (type == AccountType.CARD) "Negative debt represents credit." else "Enter the balance at your last reconciliation.") },
            signed = true, modifier = Modifier.fillMaxWidth()) }
        item { SettingsRow("Reconcile balance now", "Only later confirmed transactions will change this balance",
            checked = reconcile, onCheckedChange = { reconcile = it }) }
        item { SettingsRow("Free transfers", "Account information; no fee is inferred", checked = free, onCheckedChange = { free = it }) }
        item { Text("Linked finance apps", style = MaterialTheme.typography.titleMedium) }
        if (s.apps.none { it.finance || it.packageName in links }) item {
            TextButton(onClick = { model.navigate("finance-apps") }) { Text("Choose finance apps") }
        }
        items(s.apps.filter { it.finance || it.packageName in links }, key = { it.packageName }) { app ->
            SettingsRow(app.label, "Linking does not enable notification access",
                checked = app.packageName in links, onCheckedChange = { checked -> links = if (checked) links + app.packageName else links - app.packageName })
        }
        if (original != null) item { SettingsRow("Archived", "Keep history; hide from new transactions", checked = archived, onCheckedChange = { archived = it }) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { Button(enabled = !busy, onClick = {
            val balance = balanceInput(amount)
            val dueDay = due.takeIf { it.isNotBlank() }?.toIntOrNull()
            val statementDay = statement.takeIf { it.isNotBlank() }?.toIntOrNull()
            error = when {
                balance == null -> "Enter a balance with at most two decimal places."
                type == AccountType.CARD && due.isNotBlank() && dueDay !in 1..31 -> "Due day must be 1–31."
                type == AccountType.CARD && statement.isNotBlank() && statementDay !in 1..31 -> "Statement day must be 1–31."
                s.accounts.any { it.id != id && it.name.equals(name.trim(), true) } -> "Choose a unique account name."
                else -> null
            }
            if (error == null && balance != null) {
                val account = Account(id, name, type, cardType.trim().takeIf { type == AccountType.CARD && it.isNotEmpty() },
                    lastFour.trim().takeIf { it.isNotEmpty() }, free, balance,
                    if (reconcile) Clock.System.now() else original?.balanceAsOf ?: Clock.System.now(),
                    dueDay.takeIf { type == AccountType.CARD }, statementDay.takeIf { type == AccountType.CARD }, archived, links)
                try { account.validate(); model.save(account) } catch (e: IllegalArgumentException) { error = e.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save account") } }
        if (original != null) item { TextButton(onClick = { deleting = true }, enabled = !busy) { Text("Delete unused account") } }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete account?") },
        text = { Text("Accounts with transactions or captured drafts must be archived instead.") },
        confirmButton = { TextButton(onClick = { deleting = false; model.deleteAccount(id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}

@Composable
fun CategoriesScreen(model: LedgerSettingsModel) {
    val s by model.state.collectAsState()
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TransactionType.INCOME, TransactionType.EXPENSE).forEach { item ->
                FilterChip(type == item, { type = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        } }
        item { Button(onClick = { model.navigate("category/0/${type.name}") }, Modifier.fillMaxWidth()) { Text("Add category") } }
        items(s.categories.filter { it.type == type }, key = { it.id }) { category ->
            SettingsRow(category.name, if (category.archived) "Archived" else category.budgetMinor?.let { "Monthly budget · ${money(it)}" } ?: "",
                onClick = { model.navigate("category/${category.id}/${type.name}") })
        }
    }
}

@Composable
fun CategoryEditorScreen(model: LedgerSettingsModel, id: Long, type: TransactionType, monthlyBudget: Long?, monthlyBudgetLoaded: Boolean) {
    val s by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    if (!s.loaded || !monthlyBudgetLoaded) { CircularProgressIndicator(); return }
    val original = s.categories.find { it.id == id }
    if (id != 0L && original == null) { Text("Category no longer exists."); return }
    var name by remember(id) { mutableStateOf(original?.name.orEmpty()) }
    var archived by remember(id) { mutableStateOf(original?.archived ?: false) }
    var budget by remember(id) { mutableStateOf(original?.budgetMinor?.let(::amountText).orEmpty()) }
    val otherBudgets = s.categories.filter { it.id != id && it.type == TransactionType.EXPENSE }.sumOf { it.budgetMinor ?: 0L }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(name, { name = it }, enabled = original?.name != "Other", label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (type == TransactionType.EXPENSE) item { MoneyField(budget, { budget = it }, label = { Text("Monthly budget (PHP, optional)") },
            modifier = Modifier.fillMaxWidth()) }
        if (original != null) item { SettingsRow("Archived", "Preserve history and budgets; hide from new entries",
            checked = archived, onCheckedChange = if (original.name != "Other") { { archived = it } } else null) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { Button(enabled = !busy, onClick = {
            val minor = budget.takeIf { it.isNotBlank() }?.let(::parseAmountMinor)
            error = when {
                name.isBlank() -> "Enter a category name."
                budget.isNotBlank() && minor == null -> "Enter a budget above zero."
                monthlyBudget != null && minor != null && minor > monthlyBudget - otherBudgets ->
                    "Only ${money((monthlyBudget - otherBudgets).coerceAtLeast(0L))} of your ${money(monthlyBudget)} monthly budget is unallocated."
                s.categories.any { it.id != id && it.type == type && it.name.equals(name.trim(), true) } -> "This category already exists."
                else -> null
            }
            if (error == null) model.save(Category(id, name.trim(), type, archived, minor))
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save category") } }
    }
}

@Composable
fun AccountReviewScreen(model: LedgerSettingsModel) {
    val s by model.state.collectAsState()
    var discard by remember { mutableStateOf<CapturedDraft?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Select an account and check the details before confirming. These drafts are not counted in your balance.") }
        if (s.loaded && s.drafts.isEmpty()) item { Text("No drafts need account assignment.") }
        items(s.drafts, key = { it.id }) { draft ->
            Column {
                SettingsRow(draft.title, "${money(draft.amountMinor)} · ${draft.type.name.lowercase()}" + (draft.accountHint?.let { " · Ending $it" } ?: ""),
                    onClick = { model.navigate("draft/${draft.id}") })
                TextButton(onClick = { discard = draft }) { Text("Discard draft") }
            }
        }
    }
    discard?.let { draft -> AlertDialog(onDismissRequest = { discard = null }, title = { Text("Discard draft?") },
        confirmButton = { TextButton(onClick = { model.discardDraft(draft.id); discard = null }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = null }) { Text("Cancel") } }) }
}
