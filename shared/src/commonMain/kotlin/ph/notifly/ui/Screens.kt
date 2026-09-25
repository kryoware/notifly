@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

private val CATEGORIES = listOf("Income", "Food", "Transport", "Bills", "Shopping", "Other")
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
    val supporting = transaction.title + if (needsReview) " · Needs review" else ""
    val onClick = if (selectionMode) onToggleSelection else open
    ListItem(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "${transaction.title}, ${money(transaction.amountMinor)}${if (needsReview) ", needs review" else ""}" },
        leadingContent = {
            if (selectionMode) {
                Checkbox(selected, onCheckedChange = { onToggleSelection() })
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(sender.take(1), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        headlineContent = { Text(sender, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = if (needsReview) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(occurredOn.toString(), style = MaterialTheme.typography.labelSmall)
                    Text((if (transaction.type == TransactionType.INCOME) "+" else if (transaction.type == TransactionType.EXPENSE) "−" else "") + money(transaction.amountMinor), color = color)
                }
                if (!selectionMode && needsReview && confirm != null) {
                    IconButton(onClick = confirm) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm ${transaction.title}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    )
}

@Composable
fun HomeScreen(model: HomeModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    val pendingTotal = s.rows.filter { it.status == TransactionStatus.NEEDS_REVIEW }
        .sumOf { if (it.type == TransactionType.INCOME) it.amountMinor else -it.amountMinor }
    val listState = rememberLazyListState()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { model.navigate("edit/0") },
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add transaction") },
            )
        }
    ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), state = listState,
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
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { model.navigate("transactions") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    leadingContent = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                    headlineContent = { Text("$pending transaction${if (pending == 1) "" else "s"} need review", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                    supportingContent = { Text("Parsed automatically — confirm or edit them", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                )
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
fun TransactionsScreen(model: TransactionsModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    val listState = rememberLazyListState()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedRows = s.rows.filter { it.id in selectedIds }
    LaunchedEffect(s.rows) { selectedIds = selectedIds.intersect(s.rows.map { it.id }.toSet()) }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { model.navigate("edit/0") },
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add transaction") },
            )
        }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selectedIds.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { model.confirmAll(selectedRows); selectedIds = emptySet() },
                    enabled = selectedRows.any { it.status == TransactionStatus.NEEDS_REVIEW },
                ) { Icon(Icons.Default.Check, contentDescription = "Confirm selected transactions") }
                IconButton(onClick = { model.deleteAll(selectedRows); selectedIds = emptySet() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected transactions")
                }
                TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TransactionFilter.entries.forEachIndexed { index, f ->
                    SegmentedButton(
                        selected = s.filter == f,
                        onClick = { model.filter(f) },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionFilter.entries.size),
                        label = { Text(when (f) {
                            TransactionFilter.ALL -> "All"; TransactionFilter.NEEDS_REVIEW -> "Needs review"
                            TransactionFilter.INCOME -> "Income"; TransactionFilter.EXPENSE -> "Expense"
                        }) },
                    )
                }
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
                            backgroundContent = {
                                val toConfirm = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                                        .background(if (toConfirm) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = if (toConfirm) Alignment.CenterStart else Alignment.CenterEnd,
                                ) { Icon(if (toConfirm) Icons.Default.Check else Icons.Default.Delete, contentDescription = null) }
                            },
                            content = { row() },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun InsightsScreen(model: InsightsModel) {
    val s by model.state.collectAsState()
    val expenses = s.rows.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }.entries.sortedByDescending { it.value }
    val max = expenses.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Spending by category", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Confirmed transactions only. Transfers are excluded.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(expenses.toList(), key = { it.key }) { (category, amount) ->
            val progressPercent = amount * 100L / max
            Column(Modifier.fillMaxWidth().animateItem(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category, Modifier.weight(1f)); Text(money(amount))
                }
                LinearProgressIndicator(
                    // Compose's progress API requires Float; monetary totals stay Long through the calculation.
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
            }
        }
        if (expenses.isEmpty()) item { Text("No confirmed expenses yet.") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income"); Text(money(s.rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expenses"); Text(money(expenses.sumOf { it.value }))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Balance", style = MaterialTheme.typography.titleMedium)
                        Text(money(s.net), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Recent activity", style = MaterialTheme.typography.headlineSmall)
                Text("Last 7 days", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(s.timeline, key = { it.id }) { transaction ->
            TimelineTransactionRow(transaction, Modifier.animateItem())
        }
        if (s.timeline.isEmpty()) item { Text("No confirmed transactions in the last 7 days.") }
        if (s.timelineHasMore) item {
            OutlinedButton(onClick = model::showMoreTimeline, modifier = Modifier.fillMaxWidth()) { Text("Show 25 more") }
        }
    }
}

@Composable
private fun TimelineTransactionRow(transaction: Transaction, modifier: Modifier = Modifier) {
    val color = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.accents.income
        TransactionType.EXPENSE -> MaterialTheme.accents.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val kind = when (transaction.type) {
        TransactionType.INCOME -> "Income"
        TransactionType.EXPENSE -> "Expense"
        TransactionType.TRANSFER -> "Transfer"
    }
    val date = transaction.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    ListItem(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
        leadingContent = {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(kind.take(1), color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        headlineContent = { Text(transaction.title) },
        supportingContent = { Text("$kind · ${transaction.category}", color = color) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(date.toString(), style = MaterialTheme.typography.labelSmall)
                Text((if (transaction.type == TransactionType.INCOME) "+" else if (transaction.type == TransactionType.EXPENSE) "−" else "") + money(transaction.amountMinor), color = color)
            }
        },
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onValueChange(option) })
            }
        }
    }
}

@Composable
private fun DateTimeFields(date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = date, onValueChange = {}, readOnly = true, label = { Text("Date") },
            trailingIcon = { IconButton(onClick = { showDate = true }) { Icon(Icons.Default.CalendarToday, contentDescription = "Choose date") } },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = formatTime(time), onValueChange = {}, readOnly = true, label = { Text("Time") },
            trailingIcon = { IconButton(onClick = { showTime = true }) { Icon(Icons.Default.Schedule, contentDescription = "Choose time") } },
            modifier = Modifier.weight(1f),
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
        val parts = time.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(timeState.hour.toString().padStart(2, '0') + ":" + timeState.minute.toString().padStart(2, '0'))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
fun EditorScreen(model: EditorModel) {
    val s by model.state.collectAsState()
    var delete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (s.original == null) titleFocus.requestFocus() }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AnimatedVisibility(s.original?.status == TransactionStatus.NEEDS_REVIEW || s.sourceText != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (s.original?.status == TransactionStatus.NEEDS_REVIEW)
                            Text("Parsed on your device. Check the details before confirming.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        s.sourceText?.let { text ->
                            Text("Source notification (device only): $text",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
        item {
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
        }
        item {
            OutlinedTextField(s.title, { model.edit(title = it) }, label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { amountFocus.requestFocus() }))
        }
        item {
            OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text("Amount (PHP)") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (s.ready && !s.saving) model.save() }))
        }
        item { CategoryField(s.category) { model.edit(category = it) } }
        item { DateTimeFields(s.date, s.time, onDate = { model.edit(date = it) }, onTime = { model.edit(time = it) }) }
        item { Text("Source: ${s.original?.sourceApp ?: s.sourceApp ?: "Manual"}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            AnimatedVisibility(s.error != null) {
                s.error?.let { error -> Text(error, color = MaterialTheme.colorScheme.error) }
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

private fun formatTime(value: String): String {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return value
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when (val twelveHour = hour % 12) { 0 -> 12; else -> twelveHour }
    return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { Column(content = content) }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle == null) null else { { Text(subtitle, color = subtitleColor) } },
        leadingContent = null,
        trailingContent = when {
            checked != null -> { { Switch(checked = checked, onCheckedChange = null) } }
            onClick != null -> { {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            } }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().then(when {
            checked != null && onCheckedChange != null -> Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            onClick != null -> Modifier.clickable(onClick = onClick)
            else -> Modifier
        }),
    )
}

@Composable
fun SettingsScreen(
    model: SettingsModel,
    permissionAvailable: Boolean,
    requestPermission: () -> Unit,
    versionName: String,
    isDebugBuild: Boolean,
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
            SettingsGroup {
                SettingsRow(
                    if (permissionAvailable) "Notification access enabled" else "Notification access disabled",
                    if (permissionAvailable) "Listener: $connection" else "Listener: $connection · Manual entry still works",
                    subtitleColor = if (connection == "Connected") MaterialTheme.accents.income else MaterialTheme.colorScheme.error,
                )
                HorizontalDivider()
                SettingsRow("Manage notification access", onClick = requestPermission)
                HorizontalDivider()
                SettingsRow("Allowed apps", "Choose which notifications Notifly reads", onClick = { model.navigate("allow-list") })
            }
        }

        item { SettingsSection("Appearance") }
        item {
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Theme mode", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                HorizontalDivider()
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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

        item { SettingsSection("Capture & privacy") }
        item {
            SettingsGroup {
                SettingsRow(
                    "Offline mode", "Cloud sync is not configured yet.",
                    checked = s.offline,
                    onCheckedChange = model::offline,
                )
                HorizontalDivider()
                SettingsRow("${s.pending} changes waiting to sync", "Cloud sync is not configured.")
                HorizontalDivider()
                SettingsRow(
                    "Send crash reports",
                    "Reports contain stack traces and device info only — never notification text.",
                    checked = s.crashReporting,
                    onCheckedChange = model::crashReporting,
                )
            }
        }

        item { SettingsSection("Support") }
        item {
            SettingsGroup {
                SettingsRow("Help", "Guides for setting up capture", onClick = { uriHandler.openUri(HELP_URL) })
                HorizontalDivider()
                SettingsRow("Report a bug", "Tell us what went wrong", onClick = { uriHandler.openUri(BUG_REPORT_URL) })
            }
        }

        item { SettingsSection("About") }
        item {
            SettingsGroup {
                SettingsRow("Version", versionName)
                HorizontalDivider()
                SettingsRow("Release notes", "What changed in each version", onClick = { uriHandler.openUri(RELEASE_NOTES_URL) })
            }
        }

        if (isDebugBuild) {
            item { SettingsSection("Developer") }
            item {
                SettingsGroup {
                    SettingsRow("Notification log", "See what was captured and what couldn't be read", onClick = { model.navigate("log") })
                    HorizontalDivider()
                    SettingsRow("Replay onboarding", "Restart the first-run flow", onClick = model::restartOnboarding)
                    HorizontalDivider()
                    SettingsRow("Theme palettes", "Render every palette side by side", onClick = { model.navigate("themes") })
                    HorizontalDivider()
                    SettingsRow("Build", "debug")
                }
            }
        }
    }
}

@Composable
fun AllowListScreen(model: AllowListModel, onboarding: Boolean = false) {
    val s by model.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Text("Only apps you explicitly enable can create captures.", Modifier.padding(vertical = 8.dp)) }
        items(s.apps, key = { it.packageName }) { app ->
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = { Text("${app.kind} · ${app.capturedCount} captures") },
                trailingContent = { Switch(app.listening, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = app.listening,
                    role = Role.Switch,
                    onValueChange = { model.toggle(app) },
                ),
            )
        }
        if (s.apps.isEmpty()) item { Text("No installed apps available.", Modifier.padding(vertical = 24.dp)) }
        if (onboarding) item { Button(onClick = { model.navigate("auth") }, modifier = Modifier.padding(vertical = 12.dp)) { Text("Continue") } }
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
    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }.collect { model.edit(password = it) }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (s.signup) "Create account" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
        if (demo) Text("Demo account flow — no account will be created.")
        OutlinedTextField(s.email, { model.edit(email = it) }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedSecureTextField(password, label = { Text("Password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { model.submit() }) { Text(if (s.signup) "Create account" else "Sign in") }
        TextButton(onClick = { model.edit(signup = !s.signup) }) { Text(if (s.signup) "I already have an account" else "Create account") }
        OutlinedButton(onClick = { model.startOffline() }) { Text("Continue offline") }
    }
}
