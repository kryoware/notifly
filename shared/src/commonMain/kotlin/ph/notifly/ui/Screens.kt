@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.*
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import ph.notifly.ui.theme.hint
import ph.notifly.ui.theme.accents

private val CATEGORIES = listOf("Income", "Food", "Transport", "Bills", "Shopping", "Transfer", "Other")
private val FAB_CLEARANCE = 88.dp

@Composable
fun TransactionRow(
    transaction: Transaction,
    appLabels: Map<String, String>,
    open: () -> Unit,
    confirm: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val color = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.accents.income
        TransactionType.EXPENSE -> MaterialTheme.accents.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val needsReview = transaction.status == TransactionStatus.NEEDS_REVIEW
    val sourceApp = transaction.sourceApp?.let { appLabels[it] ?: it }
    val sender = sourceApp ?: "Manual"
    val occurredOn = transaction.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val supportingText = listOfNotNull(transaction.category.takeIf { it.isNotBlank() }, "Needs review".takeIf { needsReview })
        .joinToString(" · ")
    val leading: @Composable () -> Unit = {
        Crossfade(selected, label = "avatar") { checked ->
            if (checked) Icon(painterResource(Res.drawable.symbol_check_circle), contentDescription = null,
                Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            else AppIcon(transaction.sourceApp, sender)
        }
    }
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else ListItemDefaults.colors().containerColor,
        label = "selection",
    )
    val colors = ListItemDefaults.colors(containerColor = container, selectedContainerColor = container)
    val supporting: (@Composable () -> Unit)? = if (supportingText.isEmpty()) null else { {
            Text(supportingText, style = MaterialTheme.typography.bodySmall,
                color = if (needsReview) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    val trailing: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(occurredOn.toString(), style = MaterialTheme.typography.labelSmall)
                    Text((if (transaction.type == TransactionType.INCOME) "+" else if (transaction.type == TransactionType.EXPENSE) "−" else "") + money(transaction.amountMinor), color = color)
                }
                if (!selectionMode && needsReview && confirm != null) {
                    IconTooltip("Confirm ${transaction.title}") {
                        IconButton(onClick = confirm) {
                            Icon(painterResource(Res.drawable.symbol_check), contentDescription = "Confirm ${transaction.title}", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
    }
    val rowModifier = modifier.fillMaxWidth()
        .semantics { contentDescription = "${transaction.title}, ${money(transaction.amountMinor)}${if (needsReview) ", needs review" else ""}" }
    if (selectionMode) {
        ListItem(checked = selected, onCheckedChange = { onToggleSelection() }, modifier = rowModifier, colors = colors,
            leadingContent = leading, supportingContent = supporting, trailingContent = trailing,
            content = { Text(transaction.title, style = MaterialTheme.typography.titleMedium) })
    } else {
        ListItem(onClick = open, onLongClick = onLongClick, onLongClickLabel = "Select transaction",
            modifier = rowModifier, colors = colors, leadingContent = leading, supportingContent = supporting,
            trailingContent = trailing, content = { Text(transaction.title, style = MaterialTheme.typography.titleMedium) })
    }
}

@Composable
fun HomeScreen(model: HomeModel, appLabels: Map<String, String> = emptyMap(),
               snackbar: SnackbarHostState? = null, demo: Boolean = false) {
    val s by model.state.collectAsState()
    val pendingTotal = s.rows.filter { it.status == TransactionStatus.NEEDS_REVIEW }
        .sumOf { if (it.type == TransactionType.INCOME) it.amountMinor else -it.amountMinor }
    val listState = rememberLazyListState()
    var editingAccount by remember { mutableStateOf<AccountBalance?>(null) }
    editingAccount?.let { account ->
        BalanceDialog(account, dismiss = { editingAccount = null }) { model.setBalance(account.app.packageName, it); editingAccount = null }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (demo) "Notifly · Demo" else "Notifly") }) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { model.navigate("edit/0") },
                expanded = !listState.canScrollBackward,
                icon = { Icon(painterResource(Res.drawable.symbol_add), contentDescription = if (listState.canScrollBackward) "Add transaction" else null) },
                text = { Text("Add transaction") },
            )
        }
    ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 16.dp), state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        item {
            Card(Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Confirmed balance", style = MaterialTheme.typography.labelLarge)
                    Text(money(s.net), style = MaterialTheme.typography.headlineLarge)
                    Text("Transfers and transactions awaiting review are excluded.", style = MaterialTheme.typography.bodySmall)
                    val pending = s.rows.count { it.status == TransactionStatus.NEEDS_REVIEW }
                    if (pending > 0) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Pending review", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(money(pendingTotal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        val pending = s.rows.count { it.status == TransactionStatus.NEEDS_REVIEW }
        item {
            AnimatedVisibility(pending > 0) {
                ListItem(
                    onClick = { model.navigate("transactions") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    leadingContent = { Icon(painterResource(Res.drawable.symbol_notifications_active), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                    content = { Text("$pending transaction${if (pending == 1) "" else "s"} need review", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                    supportingContent = { Text("Parsed automatically — confirm or edit them", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                )
            }
        }
        if (s.accounts.isNotEmpty()) {
            item { AccountsHeader(s.accounts.sumOf { it.estimate }) }
            items(s.accounts, key = { "account:" + it.app.packageName }) { account ->
                AccountRow(account) { editingAccount = account }
            }
        }
        item { Text("Recent", style = MaterialTheme.typography.titleLarge) }
        items(s.rows.take(5), key = { it.id }) { t ->
            TransactionRow(t, appLabels, { model.navigate("edit/${t.id}") },
                confirm = if (t.status == TransactionStatus.NEEDS_REVIEW) { { model.confirm(t) } } else null)
        }
        if (s.rows.isEmpty()) item { Text("No transactions yet. Add one manually to get started.") }
        if (s.rows.size > 5) item { TextButton(onClick = { model.navigate("transactions") }) { Text("See all transactions") } }
    }
    }
}

@Composable
private fun AccountsHeader(total: Long) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(money(total), style = MaterialTheme.typography.titleMedium)
        }
        Text("Estimated from confirmed transactions; transfers aren't counted. Tap an account to enter its actual balance.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountRow(account: AccountBalance, edit: () -> Unit) {
    val source = account.manual?.let { "Balance of ${money(it.minor)} set ${it.setAt.toLocalDateTime(TimeZone.currentSystemDefault()).date}" }
        ?: "From captured transactions"
    ListItem(
        onClick = edit,
        leadingContent = { AppIcon(account.app.packageName, account.app.label) },
        supportingContent = { Text(source, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Text(money(account.estimate), style = MaterialTheme.typography.titleMedium) },
        content = { Text(account.app.label, style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
private fun BalanceDialog(account: AccountBalance, dismiss: () -> Unit, save: (Long?) -> Unit) {
    var text by remember { mutableStateOf(amountText(account.estimate.coerceAtLeast(0L))) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("${account.app.label} balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter what the app shows now. Confirmed transactions from here on are added to it.")
                OutlinedTextField(
                    value = text, onValueChange = { text = it; invalid = false },
                    label = { Text("Balance") }, prefix = { Text("₱") }, singleLine = true, isError = invalid,
                    supportingText = if (invalid) { { Text("Enter an amount with at most two decimal places.") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = {
            // parseAmountMinor rejects zero, which is a real balance here.
            val minor = parseAmountMinor(text) ?: 0L.takeIf { Regex("""0+(\.0{1,2})?""").matches(text.trim()) }
            if (minor == null) invalid = true else save(minor)
        }) { Text("Save") } },
        dismissButton = {
            Row {
                if (account.manual != null) TextButton(onClick = { save(null) }) { Text("Reset") }
                TextButton(onClick = dismiss) { Text("Cancel") }
            }
        },
    )
}

private fun TransactionFilter.display(): String = when (this) {
    TransactionFilter.ALL -> "All"
    TransactionFilter.NEEDS_REVIEW -> "Needs review"
    TransactionFilter.INCOME -> "Income"
    TransactionFilter.EXPENSE -> "Expense"
    TransactionFilter.TRANSFER -> "Transfer"
}

private fun TransactionFilter.icon() = when (this) {
    TransactionFilter.ALL -> Res.drawable.symbol_list
    TransactionFilter.NEEDS_REVIEW -> Res.drawable.symbol_pending_actions
    TransactionFilter.INCOME -> Res.drawable.symbol_south_west
    TransactionFilter.EXPENSE -> Res.drawable.symbol_north_east
    TransactionFilter.TRANSFER -> Res.drawable.symbol_swap_horiz
}

@Composable
fun TransactionsScreen(model: TransactionsModel, appLabels: Map<String, String> = emptyMap(),
                       snackbar: SnackbarHostState? = null, demo: Boolean = false) {
    val s by model.state.collectAsState()
    val listState = rememberLazyListState()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedRows = s.rows.filter { it.id in selectedIds }
    LaunchedEffect(s.rows) { selectedIds = selectedIds.intersect(s.rows.map { it.id }.toSet()) }
    Scaffold(
        topBar = {
            if (selectionMode) TopAppBar(
                title = { Text("${selectedIds.size} selected") },
                navigationIcon = { IconTooltip("Cancel selection") { IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(painterResource(Res.drawable.symbol_clear), contentDescription = "Cancel selection")
                } } },
                actions = {
                    IconTooltip("Confirm selected transactions") { IconButton(onClick = { model.confirmAll(selectedRows); selectedIds = emptySet() },
                        enabled = selectedRows.any { it.status == TransactionStatus.NEEDS_REVIEW }) {
                        Icon(painterResource(Res.drawable.symbol_check), contentDescription = "Confirm selected transactions")
                    } }
                    IconTooltip("Delete selected transactions") { IconButton(onClick = { confirmBulkDelete = true }) {
                        Icon(painterResource(Res.drawable.symbol_delete), contentDescription = "Delete selected transactions")
                    } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) else TopAppBar(title = { Text(if (demo) "Transactions · Demo" else "Transactions") })
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { model.navigate("edit/0") },
                expanded = !listState.canScrollBackward,
                icon = { Icon(painterResource(Res.drawable.symbol_add), contentDescription = if (listState.canScrollBackward) "Add transaction" else null) },
                text = { Text("Add transaction") },
            )
        }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            TransactionFilter.entries.forEachIndexed { index, f ->
                SegmentedButton(
                    selected = s.filter == f,
                    onClick = { model.filter(f) },
                    shape = SegmentedButtonDefaults.itemShape(index, TransactionFilter.entries.size),
                    icon = { Icon(painterResource(f.icon()), contentDescription = null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                    label = { Text(f.display()) },
                )
            }
        }
        if (s.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                Text("Transactions parsed from your allowed apps will show up here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { model.navigate("edit/0") }) { Text("Add one manually") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState,
                contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
                items(s.rows, key = { it.id }) { t ->
                    val toggle = { selectedIds = if (t.id in selectedIds) selectedIds - t.id else selectedIds + t.id }
                    val row = @Composable {
                        TransactionRow(
                            t, appLabels, { model.navigate("edit/${t.id}") },
                            confirm = if (t.status == TransactionStatus.NEEDS_REVIEW) { { model.confirm(t) } } else null,
                            modifier = Modifier.animateItem(),
                            selectionMode = selectionMode,
                            selected = t.id in selectedIds,
                            onToggleSelection = toggle,
                            onLongClick = { selectedIds = selectedIds + t.id },
                        )
                    }
                    if (selectionMode) {
                        row()
                    } else {
                        val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (t.status != TransactionStatus.NEEDS_REVIEW) return@rememberSwipeToDismissBoxState false
                                    model.confirm(t)
                                    true
                                }
                                SwipeToDismissBoxValue.EndToStart -> { model.delete(t); true }
                                SwipeToDismissBoxValue.Settled -> false
                            }
                        })
                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.semantics {
                                customActions = buildList {
                                    if (t.status == TransactionStatus.NEEDS_REVIEW)
                                        add(CustomAccessibilityAction("Confirm transaction") { model.confirm(t); true })
                                    add(CustomAccessibilityAction("Delete transaction") { model.delete(t); true })
                                }
                            },
                            backgroundContent = {
                                val toConfirm = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                Box(
                                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                                        .background(if (toConfirm) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = if (toConfirm) Alignment.CenterStart else Alignment.CenterEnd,
                                ) { Icon(if (toConfirm) painterResource(Res.drawable.symbol_check) else painterResource(Res.drawable.symbol_delete),
                                    contentDescription = null, tint = if (toConfirm) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer) }
                            },
                            content = { row() },
                        )
                    }
                }
            }
        }
    }
    }
    if (confirmBulkDelete) AlertDialog(
        onDismissRequest = { confirmBulkDelete = false },
        title = { Text("Delete ${selectedRows.size} transactions?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            model.deleteAll(selectedRows)
            selectedIds = emptySet()
            confirmBulkDelete = false
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryField(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (value in CATEGORIES) CATEGORIES else CATEGORIES + value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onValueChange(option) })
            }
        }
    }
}

@Composable
internal fun DateTimeFields(date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit,
                           dateError: String? = null, timeError: String? = null,
                           focusDateError: Boolean = false, focusTimeError: Boolean = false) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val is24Hour = is24HourClock()
    val dateFocus = remember { FocusRequester() }
    val timeFocus = remember { FocusRequester() }
    LaunchedEffect(focusDateError, focusTimeError) {
        if (focusDateError) dateFocus.requestFocus()
        else if (focusTimeError) timeFocus.requestFocus()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = date, onValueChange = {}, readOnly = true, label = { Text("Date") },
            isError = dateError != null, supportingText = dateError?.let { { Text(it) } },
            trailingIcon = { IconTooltip("Choose date") { IconButton(onClick = { showDate = true }) {
                Icon(painterResource(Res.drawable.symbol_calendar_today), contentDescription = "Choose date")
            } } },
            modifier = Modifier.fillMaxWidth().focusRequester(dateFocus),
        )
        OutlinedTextField(
            value = formatTime(time, is24Hour), onValueChange = {}, readOnly = true, label = { Text("Time") },
            isError = timeError != null, supportingText = timeError?.let { { Text(it) } },
            trailingIcon = { IconTooltip("Choose time") { IconButton(onClick = { showTime = true }) {
                Icon(painterResource(Res.drawable.symbol_schedule), contentDescription = "Choose time")
            } } },
            modifier = Modifier.fillMaxWidth().focusRequester(timeFocus),
        )
    }
    if (showDate) {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        val initialMillis = parsed?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onDate(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showTime) {
        var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
        val parts = time.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            title = { TimePickerDialogDefaults.Title(displayMode) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(timeState.hour.toString().padStart(2, '0') + ":" + timeState.minute.toString().padStart(2, '0'))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            modeToggleButton = { TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { displayMode = if (displayMode == TimePickerDisplayMode.Picker)
                    TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker },
                displayMode = displayMode,
            ) },
        ) {
            BoxWithConstraints {
                if (displayMode == TimePickerDisplayMode.Picker && maxHeight >= TimePickerDialogDefaults.MinHeightForTimePicker)
                    TimePicker(state = timeState)
                else TimeInput(state = timeState)
            }
        }
    }
}

@Composable
fun EditorScreen(model: EditorModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    var delete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (s.original == null) titleFocus.requestFocus() }
    LaunchedEffect(s.titleError, s.amountError, s.dateError, s.timeError) {
        when {
            s.titleError != null -> { listState.animateScrollToItem(2); titleFocus.requestFocus() }
            s.amountError != null -> { listState.animateScrollToItem(3); amountFocus.requestFocus() }
            s.dateError != null || s.timeError != null -> listState.animateScrollToItem(5)
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AnimatedVisibility(s.original?.status == TransactionStatus.NEEDS_REVIEW || s.sourceText != null || s.original?.captureId != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (s.original?.status == TransactionStatus.NEEDS_REVIEW)
                            Text("Parsed on your device. Check the details before confirming.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        if (s.sourceText != null || s.original?.captureId != null)
                            Text("Source notification (device only): ${s.sourceText ?: "Raw text not retained. Turn on \"Keep raw text on device\" in the notification log to keep it for future notifications."}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState())) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = s.type == type,
                        onClick = { model.edit(type = type) },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(s.title, { model.edit(title = it) }, label = { Text("Description") },
                isError = s.titleError != null, supportingText = s.titleError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { amountFocus.requestFocus() }))
        }
        item {
            OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text("Amount (PHP)") },
                isError = s.amountError != null, supportingText = s.amountError?.let { { Text(it) } },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (s.ready && !s.saving) model.save() }))
        }
        item { CategoryField(s.category) { model.edit(category = it) } }
        item { DateTimeFields(s.date, s.time, onDate = { model.edit(date = it) }, onTime = { model.edit(time = it) },
            dateError = s.dateError, timeError = s.timeError,
            focusDateError = s.titleError == null && s.amountError == null && s.dateError != null,
            focusTimeError = s.titleError == null && s.amountError == null && s.dateError == null && s.timeError != null) }
        item {
            val sourceApp = s.original?.sourceApp ?: s.sourceApp
            val sourceLabel = sourceApp?.let { appLabels[it] ?: it } ?: "Manual"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(appLabels.packageFor(sourceApp), sourceLabel, size = 24.dp)
                Text("Source: $sourceLabel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AnimatedVisibility(s.error != null && listOf(s.titleError, s.amountError, s.dateError, s.timeError).all { it == null }) {
                s.error?.let { error -> Text(error, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
        item { Button(onClick = model::save, enabled = s.ready && !s.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.original?.status == TransactionStatus.NEEDS_REVIEW) "Confirm transaction" else "Save transaction")
        } }
        if (s.original != null) item {
            TextButton(onClick = { delete = true }) {
                Text("Delete transaction", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete transaction?") },
        text = { Text("You can undo this immediately after deleting.") },
        confirmButton = { TextButton(onClick = { delete = false; model.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}

private const val RELEASE_NOTES_URL = "https://github.com/kryoware/notifly/releases"
private const val HELP_URL = "https://www.google.com/search?q=notifly+help"
private const val BUG_REPORT_URL = "https://www.google.com/search?q=notifly+report+a+bug"

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

private fun formatTime(value: String, is24Hour: Boolean): String {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return value
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when (val twelveHour = hour % 12) { 0 -> 12; else -> twelveHour }
    return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    // Default segmented container is `surface`, which vanishes against the screen; only checked rows got a fill.
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
    )
    val supporting: (@Composable () -> Unit)? = subtitle?.let { { Text(it, color = subtitleColor) } }
    val trailing: (@Composable () -> Unit)? = when {
        checked != null -> { { Switch(checked = checked, onCheckedChange = null) } }
        onClick != null -> { { Icon(painterResource(Res.drawable.symbol_keyboard_arrow_right), null,
            modifier = mirroredIconModifier()) } }
        else -> null
    }
    when {
        checked != null && onCheckedChange != null -> SegmentedListItem(
            checked = checked, onCheckedChange = onCheckedChange, shapes = shapes, colors = colors,
            modifier = Modifier.fillMaxWidth(), supportingContent = supporting, trailingContent = trailing,
            content = { Text(title) })
        onClick != null -> SegmentedListItem(
            onClick = onClick, shapes = shapes, colors = colors, modifier = Modifier.fillMaxWidth(),
            supportingContent = supporting, trailingContent = trailing, content = { Text(title) })
        else -> SegmentedListItem(shapes = shapes, colors = colors, modifier = Modifier.fillMaxWidth(),
            supportingContent = supporting, trailingContent = trailing, content = { Text(title) })
    }
}

@Composable
fun SettingsScreen(
    model: SettingsModel,
    permissionAvailable: Boolean,
    requestPermission: () -> Unit,
    versionName: String,
    isDebugBuild: Boolean,
    biometricAvailable: Boolean = false,
    authenticateBiometric: (onSuccess: () -> Unit) -> Unit = {},
) {
    val s by model.state.collectAsState()
    val source = org.koin.compose.koinInject<ph.notifly.domain.source.TransactionSource>()
    val connection by source.connection.collectAsState()
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SettingsSection("Account") }
        item {
            SettingsGroup {
                SettingsRow("Account", "Sign in or keep using Notifly offline", onClick = { model.navigate("auth") })
            }
        }

        item { SettingsSection("Security") }
        item {
            var pinDialog by remember { mutableStateOf(false) }
            val rows = if (biometricAvailable) 2 else 1
            SettingsGroup {
                SettingsRow("App PIN",
                    if (s.pinSet) "Asked every time you open Notifly" else "Lock Notifly with a 6-digit PIN",
                    checked = s.pinSet, onCheckedChange = { pinDialog = true }, index = 0, count = rows)
                if (biometricAvailable) SettingsRow("Unlock with biometrics",
                    if (s.pinSet) "Use your fingerprint or face instead of the PIN" else "Set a PIN first",
                    checked = s.biometric, onCheckedChange = if (s.pinSet) { value -> if (value) authenticateBiometric { model.biometric(true) } else model.biometric(false) } else null, index = 1, count = rows)
            }
            if (pinDialog) PinDialog(setup = !s.pinSet, onDismiss = { pinDialog = false }) { pin ->
                pinDialog = false
                if (s.pinSet) model.clearPin(pin) else model.setPin(pin)
            }
        }

        item { SettingsSection("Appearance") }
        item {
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Theme mode", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { model.themeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = "${s.palette.name} · ${s.palette.hint}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Color palette") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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
        }

        item { SettingsSection("Capture") }
        item {
            SettingsGroup {
                SettingsRow(
                    if (permissionAvailable) "Notification access enabled" else "Notification access disabled",
                    if (permissionAvailable) "Listener: $connection" else "Listener: $connection · Manual entry still works",
                    subtitleColor = if (connection == "Connected") MaterialTheme.accents.income else MaterialTheme.colorScheme.error,
                    index = 0, count = 6,
                )
                SettingsRow("Manage notification access", onClick = requestPermission, index = 1, count = 6)
                SettingsRow("Allowed apps", "Choose which notifications Notifly reads", onClick = { model.navigate("allow-list") }, index = 2, count = 6)
                SettingsRow("Finance apps", "Detect transfers between your bank and wallet apps", onClick = { model.navigate("finance-apps") }, index = 3, count = 6)
                SettingsRow(
                    "Offline mode", "Cloud sync is not configured yet.",
                    checked = s.offline,
                    onCheckedChange = model::offline,
                    index = 4, count = 6,
                )
                SettingsRow("${s.pending} changes waiting to sync", "Cloud sync is not configured.", index = 5, count = 6)
            }
        }

        item { SettingsSection("Support & privacy") }
        item {
            SettingsGroup {
                SettingsRow("Help", "Guides for setting up capture", onClick = { uriHandler.openUri(HELP_URL) }, index = 0, count = 3)
                SettingsRow("Report a bug", "Tell us what went wrong", onClick = { uriHandler.openUri(BUG_REPORT_URL) }, index = 1, count = 3)
                SettingsRow(
                    "Send crash reports",
                    "Reports contain stack traces and device info only — never notification text.",
                    checked = s.crashReporting,
                    onCheckedChange = model::crashReporting,
                    index = 2, count = 3,
                )
            }
        }

        item { SettingsSection("About") }
        item {
            SettingsGroup {
                SettingsRow("Version", versionName, index = 0, count = 2)
                SettingsRow("Release notes", "What changed in each version", onClick = { uriHandler.openUri(RELEASE_NOTES_URL) }, index = 1, count = 2)
            }
        }

        if (isDebugBuild) {
            item { SettingsSection("Developer") }
            item {
                SettingsGroup {
                    SettingsRow("Notification log", "See what was captured and what couldn't be read", onClick = { model.navigate("log") }, index = 0, count = 4)
                    SettingsRow("Replay onboarding", "Restart the first-run flow", onClick = model::restartOnboarding, index = 1, count = 4)
                    SettingsRow("Theme palettes", "Render every palette side by side", onClick = { model.navigate("themes") }, index = 2, count = 4)
                    SettingsRow("Build", "debug", index = 3, count = 4)
                }
            }
        }
    }
}

@Composable
fun AllowListScreen(model: AllowListModel, onboarding: Boolean = false) {
    val s by model.state.collectAsState()
    var searchText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(if (s.finance) "Pick the bank and wallet apps you move money between. Matching in and out alerts become one transfer."
            else "Only apps you explicitly enable can create captures.", Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it; model.search(it) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search apps") },
            singleLine = true,
            leadingIcon = { Icon(painterResource(Res.drawable.symbol_search), contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) IconTooltip("Clear search") { IconButton(onClick = { searchText = ""; model.search("") }) {
                    Icon(painterResource(Res.drawable.symbol_clear), contentDescription = "Clear search")
                } }
            },
        )
    val plain = ListItemDefaults.colors().let {
        ListItemDefaults.colors(selectedContainerColor = it.containerColor, selectedContentColor = it.contentColor)
    }
    LazyColumn(Modifier.weight(1f)) {
        items(s.apps, key = { it.packageName }, contentType = { "app" }) { app ->
            val checked = with(model) { app.isChecked() }
            ListItem(
                checked = checked,
                onCheckedChange = { model.toggle(app) },
                colors = plain,
                leadingContent = { AppIcon(app.packageName, app.label) },
                content = { Text(app.label) },
                supportingContent = { Text("${app.kind} · ${app.capturedCount} captures") },
                trailingContent = { Switch(checked, onCheckedChange = null) },
            )
        }
        if (s.apps.isEmpty()) item {
            Text(
                if (s.query.isNotBlank()) "No apps match \"${s.query}\"." else if (s.finance) "Allow apps first." else "No installed apps available.",
                Modifier.padding(vertical = 24.dp),
            )
        }
        if (onboarding) item { Button(onClick = { model.navigate("auth") }, modifier = Modifier.padding(vertical = 12.dp)) { Text("Continue") } }
    }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(listOf("Stop typing your expenses", "How it works", "One permission to grant", "Keep it running")[s.page], style = MaterialTheme.typography.headlineLarge)
        Text(listOf("Track payments from the apps you choose. Your notifications stay on your device.",
            "Choose your apps. We parse payment alerts on-device. You review and confirm every transaction.",
            "Notification access lets Notifly read alerts only from allowed apps. Raw notification text is never uploaded.",
            "Android can pause background apps to save power, and some phones do it aggressively. Turning that off for Notifly keeps captures arriving promptly.")[s.page])
        LinearProgressIndicator(
            progress = { (s.page + 1) / 4f },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Step ${s.page + 1} of 4" },
        )
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
    val password = rememberTextFieldState(s.password)
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(s.emailError, s.passwordError) {
        when {
            s.emailError != null -> emailFocus.requestFocus()
            s.passwordError != null -> passwordFocus.requestFocus()
        }
    }
    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }.collect { model.edit(password = it) }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (s.signup) "Create account" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
        if (demo) Text("Demo account flow — no account will be created.")
        OutlinedTextField(s.email, { model.edit(email = it) }, label = { Text("Email") }, singleLine = true,
            isError = s.emailError != null, supportingText = s.emailError?.let { { Text(it) } },
            modifier = Modifier.focusRequester(emailFocus), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedSecureTextField(password, label = { Text("Password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = s.passwordError != null, supportingText = s.passwordError?.let { { Text(it) } },
            modifier = Modifier.focusRequester(passwordFocus))
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Button(onClick = { model.submit() }) { Text(if (s.signup) "Create account" else "Sign in") }
        TextButton(onClick = { model.edit(signup = !s.signup) }) { Text(if (s.signup) "I already have an account" else "Create account") }
        OutlinedButton(onClick = { model.startOffline() }) { Text("Continue offline") }
    }
}
