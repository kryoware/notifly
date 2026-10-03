@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.*
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import ph.notifly.ui.theme.hint
import ph.notifly.ui.theme.accents
import ph.notifly.ui.theme.tabular

private val FAB_CLEARANCE = 88.dp
private const val SWIPE_THRESHOLD = 0.5f
// The brand's full-width call to action.
private val WideButton = Modifier.fillMaxWidth().heightIn(min = 56.dp)

/**
 * Displays a transaction; [selectionMode] routes taps to [onToggleSelection] and hides confirmation.
 * Otherwise taps open the row, long presses call [onLongClick], and review rows can expose [confirm].
 * [appLabels] maps package names to display labels.
 */
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
    accountNames: Map<Long, String> = emptyMap(),
    hideAmount: Boolean = false,
) {
    val color = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.accents.income
        TransactionType.EXPENSE -> MaterialTheme.accents.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accountLabel = accountNames[transaction.accountId].orEmpty() +
        (transaction.toAccountId?.let { " → " + accountNames[it].orEmpty() } ?: "")
    val needsReview = transaction.status == TransactionStatus.NEEDS_REVIEW
    val sourceApp = transaction.sourceApp?.let { appLabels[it] ?: it }
    val sender = sourceApp ?: "Manual"
    val occurredOn = transaction.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val supportingText = listOfNotNull(accountLabel.takeIf { it.isNotBlank() }, transaction.category.takeIf { it.isNotBlank() }, shortDate(occurredOn),
        "Needs review".takeIf { needsReview }).joinToString(" · ")
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
    val supporting: @Composable () -> Unit = {
            Text(supportingText, style = MaterialTheme.typography.bodySmall,
                color = if (needsReview) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val trailing: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (transaction.type == TransactionType.INCOME) "+" else if (transaction.type == TransactionType.EXPENSE) "−" else "") + money(transaction.amountMinor),
                    style = MaterialTheme.typography.titleMedium.tabular(), color = color, modifier = hiddenMoneyModifier(hideAmount))
                if (!selectionMode && needsReview && confirm != null) {
                    IconTooltip("Confirm ${transaction.title}") {
                        IconButton(onClick = confirm, modifier = Modifier.size(48.dp).semantics { contentDescription = "Confirm ${transaction.title}" }) {
                            StatusMark(confirmed = false)
                        }
                    }
                } else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StatusMark(confirmed = !needsReview) }
            }
    }
    val rowModifier = modifier.fillMaxWidth()
        .semantics {
            contentDescription = listOfNotNull(transaction.title, money(transaction.amountMinor).takeIf { !hideAmount },
                "needs review".takeIf { needsReview }).joinToString(", ")
        }
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
               snackbar: SnackbarHostState? = null, demo: Boolean = false,
               listeningApps: List<String> = emptyList(), accessOn: Boolean = true, requestAccess: () -> Unit = {}) {
    val s by model.state.collectAsState()
    val pendingRows = s.rows.filter { it.status == TransactionStatus.NEEDS_REVIEW }
    val pendingTotal = pendingRows.sumOf { when (it.type) { TransactionType.INCOME -> it.amountMinor; TransactionType.EXPENSE -> -it.amountMinor; TransactionType.TRANSFER -> -it.feeMinor } }
    val listState = rememberLazyListState()
    var editingAccount by remember { mutableStateOf<AccountBalance?>(null) }
    editingAccount?.let { account ->
        BalanceDialog(account, hidden = s.hideAmounts, dismiss ={ editingAccount = null }) { model.setBalance(account.account.id, it); editingAccount = null }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Wordmark(if (demo) "DEMO" else null) }, actions = {
                IconTooltip("Hide amounts") {
                    IconToggleButton(checked = s.hideAmounts, onCheckedChange = model::hideAmounts, enabled = s.loaded) {
                        Icon(painterResource(if (s.hideAmounts) Res.drawable.symbol_visibility_off else Res.drawable.symbol_visibility),
                            contentDescription = "Hide amounts")
                    }
                }
            })
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = { AddTransactionFab(expanded = !listState.canScrollBackward) { model.navigate("edit/0") } }
    ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 16.dp), state = listState,
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        item { Box(Modifier.padding(bottom = 8.dp)) { BalanceHero(s.net, pendingRows.size, pendingTotal, s.hideAmounts) { model.navigate("transactions") } } }
        if (s.drafts > 0) item {
            FilledTonalButton(onClick = { model.navigate("account-review") }, modifier = Modifier.fillMaxWidth()) {
                Text("Assign accounts · ${s.drafts} to review")
            }
        }
        if (s.accounts.isNotEmpty()) {
            item { AccountsHeader(s.accounts.sumOf { it.netValue }, s.hideAmounts) }
            items(s.accounts, key = { "account:" + it.account.id }) { account ->
                AccountRow(account, s.hideAmounts) { editingAccount = account }
            }
        }
        item {
            SectionHeader("Recent") {
                if (s.rows.size > 5) TextButton(onClick = { model.navigate("transactions") }) { Text("See all") }
            }
        }
        items(s.rows.take(5), key = { it.id }) { t ->
            TransactionRow(t, appLabels, { model.navigate("edit/${t.id}") },
                accountNames = s.accounts.associate { it.account.id to it.account.name }, hideAmount = s.hideAmounts, confirm = if (t.status == TransactionStatus.NEEDS_REVIEW) { { model.confirm(t) } } else null)
        }
        // First value is the first captured draft, so the empty ledger says what it is waiting on.
        if (s.rows.isEmpty()) item {
            when {
                !accessOn -> EmptyState("Nothing counts until you do.",
                    "Notification access is off, so payment alerts can't become drafts yet. You can still add transactions manually.") {
                    FilledTonalButton(onClick = requestAccess) { Text("Turn on access") }
                }
                listeningApps.isEmpty() -> EmptyState("Nothing counts until you do.",
                    "No apps chosen yet. Pick the bank and wallet apps whose payment alerts should become drafts.") {
                    FilledTonalButton(onClick = { model.navigate("allow-list") }) { Text("Choose apps") }
                }
                else -> EmptyState("Listening for your first ping.",
                    "Your next payment alert from ${spokenList(listeningApps)} lands here as a draft. Confirm it and it counts.")
            }
        }
    }
    }
}

@Composable
private fun AddTransactionFab(expanded: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        // M3 1.5 clears the text slot's semantics, so the name must sit on the button itself.
        modifier = Modifier.semantics { contentDescription = "Add transaction" },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(painterResource(Res.drawable.symbol_add), contentDescription = null) },
        text = { Text("Add transaction") },
    )
}

/** Only confirmed money moves the headline; review items sit in their own line below it. */
@Composable
private fun BalanceHero(net: Long, pending: Int, pendingTotal: Long, hidden: Boolean, review: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = if (pending > 0) 14.dp else 22.dp)) {
            val muted = LocalContentColor.current.copy(alpha = 0.7f)
            Text("Confirmed balance", style = MaterialTheme.typography.labelLarge)
            Text(splitMoney(net, LocalContentColor.current.copy(alpha = 0.55f)),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em).tabular(),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 44.sp),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).then(hiddenMoneyModifier(hidden)))
            Text("Transfer amounts aren't counted; fees count as spending.", style = MaterialTheme.typography.bodySmall, color = muted)
            if (pending > 0) Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().background(scheme.surface.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusMark(confirmed = false, size = 16.dp)
                Column(Modifier.weight(1f)) {
                    Text("$pending to review", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    Row {
                        Text(signedMoney(pendingTotal), style = MaterialTheme.typography.bodySmall.tabular(), color = muted,
                            modifier = hiddenMoneyModifier(hidden))
                        Text(" · not counted", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                Button(onClick = review, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Review") }
            }
        }
    }
}

@Composable
private fun AccountsHeader(total: Long, hidden: Boolean) {
    Column {
        SectionHeader("Accounts") {
            Text(splitMoney(total, LocalContentColor.current.copy(alpha = 0.55f)), style = MaterialTheme.typography.titleMedium.tabular(),
                modifier = hiddenMoneyModifier(hidden))
        }
        Text("Estimated from confirmed transactions. Tap an account to enter its actual balance.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun AccountRow(account: AccountBalance, hidden: Boolean, edit: () -> Unit) {
    val source = account.manual?.let {
        val on = shortDate(it.setAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
        if (hidden) "Set on $on" else "Set to ${money(it.minor)} on $on"
    }
        ?: "From captured transactions"
    ListItem(
        onClick = edit,
        leadingContent = { AppIcon(account.account.linkedApps.firstOrNull().orEmpty(), account.account.name) },
        supportingContent = { Text((if (account.account.type == ph.notifly.domain.model.AccountType.CARD) "Debt owed · " else "") + source + if (account.account.archived) " · Archived" else "", style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Text(splitMoney(account.estimate, LocalContentColor.current.copy(alpha = 0.55f)), style = MaterialTheme.typography.titleMedium.tabular(),
                modifier = hiddenMoneyModifier(hidden))
        },
        content = { Text(account.account.name, style = MaterialTheme.typography.titleMedium) },
    )
}

/** Edits a nonnegative balance in minor units; reset passes null to [save]. The caller dismisses after saving. */
@Composable
private fun BalanceDialog(account: AccountBalance, hidden: Boolean, dismiss: () -> Unit, save: (Long?) -> Unit) {
    // Left blank while amounts are hidden so opening the editor can't reveal the estimate.
    var text by remember { mutableStateOf(if (hidden) "" else amountText(account.estimate)) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("${account.account.name} balance") },
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
            // parseAmountMinor rejects zero and negatives, both real balances here.
            val magnitude = text.trim().removePrefix("-")
            val minor = parseAmountMinor(magnitude) ?: 0L.takeIf { Regex("""0+(\.0{1,2})?""").matches(magnitude) }
            if (minor == null) invalid = true else save(if (text.trim().startsWith("-")) -minor else minor)
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
        floatingActionButton = { AddTransactionFab(expanded = !listState.canScrollBackward) { model.navigate("edit/0") } }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionFilter.entries.forEach { f ->
                FilterChip(
                    selected = s.filter == f,
                    onClick = { model.filter(f) },
                    label = { Text(f.display()) },
                    leadingIcon = { Icon(painterResource(f.icon()), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
        }
        if (s.rows.isEmpty()) {
            EmptyState("Nothing here yet", "Transactions parsed from your allowed apps will show up here.", Modifier.fillMaxSize()) {
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
                            accountNames = s.accountNames, selectionMode = selectionMode,
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
                        }, positionalThreshold = { it * SWIPE_THRESHOLD })
                        // Swipe state is saveable and LazyColumn restores it by key, so an undone delete
                        // (same id) would come back already dismissed.
                        LaunchedEffect(t) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) dismissState.reset()
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = t.status == TransactionStatus.NEEDS_REVIEW,
                            modifier = Modifier.semantics {
                                customActions = buildList {
                                    if (t.status == TransactionStatus.NEEDS_REVIEW)
                                        add(CustomAccessibilityAction("Confirm transaction") { model.confirm(t); true })
                                    add(CustomAccessibilityAction("Delete transaction") { model.delete(t); true })
                                }
                            },
                            backgroundContent = {
                                val toConfirm = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                var width by remember { mutableFloatStateOf(0f) }
                                val offset = try { dismissState.requireOffset() } catch (_: IllegalStateException) { 0f }
                                val fraction = if (width == 0f) 0f else (abs(offset) / (width * SWIPE_THRESHOLD)).coerceIn(0f, 1f)
                                val scheme = MaterialTheme.colorScheme
                                val accents = MaterialTheme.accents
                                Box(
                                    Modifier.fillMaxSize().onSizeChanged { width = it.width.toFloat() }.clip(MaterialTheme.shapes.large)
                                        .background(if (toConfirm) accents.confirmed else lerp(scheme.errorContainer, scheme.error, fraction))
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = if (toConfirm) Alignment.CenterStart else Alignment.CenterEnd,
                                ) { Icon(if (toConfirm) painterResource(Res.drawable.symbol_check) else painterResource(Res.drawable.symbol_delete),
                                    contentDescription = null, tint = if (toConfirm) accents.onConfirmed
                                    else lerp(scheme.onErrorContainer, scheme.onError, fraction)) }
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
private fun CategoryField(value: String, categories: List<String>, onValueChange: (String) -> Unit) {
    val options = if (value in categories) categories else categories + value
    ChoiceField("Category", value, options, searchable = true, label = { it }, choose = onValueChange)
}

/**
 * Picks an ISO date and an `HH:mm` time, displaying time in the device's clock format.
 * Callbacks run only on confirmation; canceling preserves the supplied values.
 */
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = date, onValueChange = {}, readOnly = true, label = { Text("Date") },
            isError = dateError != null, supportingText = dateError?.let { { Text(it) } },
            trailingIcon = { IconTooltip("Choose date") { IconButton(onClick = { showDate = true }) {
                Icon(painterResource(Res.drawable.symbol_calendar_today), contentDescription = "Choose date")
            } } },
            singleLine = true, modifier = Modifier.weight(1.2f).focusRequester(dateFocus),
        )
        OutlinedTextField(
            value = formatTime(time, is24Hour), onValueChange = {}, readOnly = true, label = { Text("Time") },
            isError = timeError != null, supportingText = timeError?.let { { Text(it) } },
            trailingIcon = { IconTooltip("Choose time") { IconButton(onClick = { showTime = true }) {
                Icon(painterResource(Res.drawable.symbol_schedule), contentDescription = "Choose time")
            } } },
            singleLine = true, modifier = Modifier.weight(1f).focusRequester(timeFocus),
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
    var sourceExpanded by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (s.original == null) titleFocus.requestFocus() }
    LaunchedEffect(s.titleError, s.amountError, s.dateError, s.timeError, s.feeError, s.accountError, s.toAccountError) {
        when {
            s.titleError != null -> { listState.animateScrollToItem(2); titleFocus.requestFocus() }
            s.amountError != null -> { listState.animateScrollToItem(3); amountFocus.requestFocus() }
            s.accountError != null || s.toAccountError != null -> listState.animateScrollToItem(4)
            s.feeError != null -> listState.animateScrollToItem(5)
            s.dateError != null || s.timeError != null -> listState.animateScrollToItem(6)
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AnimatedVisibility((s.original?.status == TransactionStatus.NEEDS_REVIEW || s.fromDraft) || s.sourceText != null || s.original?.captureId != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if ((s.original?.status == TransactionStatus.NEEDS_REVIEW || s.fromDraft)) Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusMark(confirmed = false, size = 16.dp)
                            Text("Parsed on your device. Check the details before confirming.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
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
            OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text(if (s.type == TransactionType.TRANSFER) "Amount to transfer (PHP)" else "Amount (PHP)") },
                isError = s.amountError != null, supportingText = s.amountError?.let { { Text(it) } },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (s.ready && !s.saving) model.save() }))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.type == TransactionType.TRANSFER) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(Res.drawable.symbol_swap_horiz), null, tint = MaterialTheme.colorScheme.primary)
                        Text("Move money between your accounts", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("The amount leaves From and arrives in To. Only the fee counts as spending.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AccountPicker(if (s.type == TransactionType.TRANSFER) "From account" else "Account",
                    s.accountId, s.accounts.filter { !it.archived || it.id == s.original?.accountId }, s.accountError) { model.edit(accountId = it) }
                if (s.type == TransactionType.TRANSFER) {
                    Icon(painterResource(Res.drawable.symbol_arrow_downward), null,
                        Modifier.align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    AccountPicker("To account", s.toAccountId,
                        s.accounts.filter { (!it.archived || it.id == s.original?.toAccountId) && it.id != s.accountId }, s.toAccountError) { model.edit(toAccountId = it) }
                }
                if (s.accounts.none { !it.archived }) {
                    TextButton(onClick = { model.navigate("accounts") }) { Text("Create an account") }
                }
            }
        }
        if (s.type == TransactionType.TRANSFER) item {
            val origin = s.accounts.find { it.id == s.accountId }
            if (s.feeRequired) {
                OutlinedTextField(s.fee, { model.edit(fee = it) }, label = { Text("Transfer fee (PHP) · Required") },
                    supportingText = { Text(s.feeError ?: "Charged to ${origin?.name}. Enter 0 if this transfer was free.") },
                    isError = s.feeError != null, placeholder = { Text("0.00") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } else Text(if (origin?.freeTransfer == true) "Free transfer · No fee" else "Choose a From account to set the fee.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val amount = parseAmountMinor(s.amount)
            val fee = if (!s.feeRequired) 0L else parseFeeMinor(s.fee)
            if (origin != null && amount != null && fee != null && amount <= Long.MAX_VALUE - fee) {
                Text("Total from ${origin.name}: ${money(amount + fee)}\nTo account receives: ${money(amount)}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        } else item {
            CategoryField(s.category, s.categories.filter { it.type == s.type && (!it.archived || it.id == s.original?.categoryId) }.map { it.name }) { model.edit(category = it) }
        }
        item { DateTimeFields(s.date, s.time, onDate = { model.edit(date = it) }, onTime = { model.edit(time = it) },
            dateError = s.dateError, timeError = s.timeError,
            focusDateError = s.titleError == null && s.amountError == null && s.dateError != null,
            focusTimeError = s.titleError == null && s.amountError == null && s.dateError == null && s.timeError != null) }
        item {
            val sourceLabel = s.sourceApp?.let { appLabels[it] ?: it } ?: "Manual"
            Column {
                ListItem(headlineContent = { Text("Source details") }, supportingContent = { Text(sourceLabel) },
                    trailingContent = { Icon(painterResource(Res.drawable.symbol_expand_more), null,
                        Modifier.rotate(if (sourceExpanded) 180f else 0f)) },
                    modifier = Modifier.clickable { sourceExpanded = !sourceExpanded }
                        .semantics { stateDescription = if (sourceExpanded) "Expanded" else "Collapsed" })
                AnimatedVisibility(sourceExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.original?.captureId != null || s.captureId != null) {
                            Text("Source: $sourceLabel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ChoiceField("Source app", sourceLabel,
                                listOf<String?>(null) + s.apps.filter { it.listening || it.packageName == s.sourceApp }.map { it.packageName },
                                searchable = true, searchText = { it.orEmpty() },
                                label = { pkg -> pkg?.let { appLabels[it] ?: it } ?: "Manual" }) { model.edit(sourceApp = it) }
                        }
                        if (s.sourceText != null || s.original?.captureId != null || s.captureId != null)
                            Text("Source notification (device only): ${s.sourceText ?: "Raw text not retained. Turn on \"Keep raw text on device\" in the notification log to keep it for future notifications."}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            AnimatedVisibility(s.error != null && listOf(s.titleError, s.amountError, s.dateError, s.timeError).all { it == null }) {
                s.error?.let { error -> Text(error, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
        item {
            val confirming = (s.original?.status == TransactionStatus.NEEDS_REVIEW || s.fromDraft)
            // Confirming is the one moment money starts to count, so it takes the confirmed tick's colour.
            Button(onClick = model::save, enabled = s.ready && !s.saving, modifier = WideButton,
                colors = if (confirming) ButtonDefaults.buttonColors(containerColor = MaterialTheme.accents.confirmed,
                    contentColor = MaterialTheme.accents.onConfirmed) else ButtonDefaults.buttonColors()) {
                if (confirming) Icon(painterResource(Res.drawable.symbol_check), null, Modifier.padding(end = 8.dp).size(18.dp))
                Text(if (s.type == TransactionType.TRANSFER) {
                    if (confirming || s.captureId != null) "Confirm transfer" else "Save transfer"
                } else if (confirming || s.captureId != null) "Confirm transaction" else "Save transaction")
            }
        }
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
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp).semantics { heading() },
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

/** One rounded card per group; rows inside stay square and touch, so only the card's outer corners round. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(content = content)
    }
}

/**
 * Displays a square setting row; [SettingsGroup] supplies the rounded outline.
 * A non-null [checked] with [onCheckedChange] makes the whole row toggle; otherwise [onClick] handles taps.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    external: Boolean = false,
) {
    val shapes = ListItemShapes(RectangleShape, RectangleShape, RectangleShape, RectangleShape, RectangleShape, RectangleShape)
    // Default segmented container is `surface`, which vanishes against the screen; only checked rows got a fill.
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
    )
    val supporting: (@Composable () -> Unit)? = subtitle?.let { { Text(it, color = subtitleColor) } }
    val trailing: (@Composable () -> Unit)? = when {
        checked != null -> { { Switch(checked = checked, onCheckedChange = null) } }
        onClick != null && external -> { { Icon(painterResource(Res.drawable.symbol_open_in_new), "Opens in browser") } }
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
    demo: Boolean = false,
    onDemo: (Boolean) -> Unit = {},
    transactions: ph.notifly.domain.repository.TransactionRepository? = null,
    onDataMessage: suspend (String) -> Unit = {},
    allowDataTransfer: Boolean = true,
    ledger: ph.notifly.domain.repository.LedgerRepository? = null,
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
        item { SettingsSection("Account & sync") }
        item {
            SettingsGroup {
                SettingsRow("Account", "Sign in or keep using Notifly offline", onClick = { model.navigate("auth") })
                SettingsRow(
                    "Offline mode",
                    "Cloud sync is not configured yet." + if (s.pending > 0) " ${s.pending} changes waiting to sync." else "",
                    checked = s.offline,
                    onCheckedChange = model::offline,
                )
            }
        }

        item { SettingsSection("General") }
        item {
            SettingsGroup {
                SettingsRow("Accounts", "Bank, card and wallet balances and app links", onClick = { model.navigate("accounts") })
                SettingsRow("Categories", "Customize income and expense categories", onClick = { model.navigate("categories") })
                SettingsRow("Assign accounts", "Review captured transactions without an account", onClick = { model.navigate("account-review") })
                SettingsRow("Category budgets", "Set a monthly limit for each spending category", onClick = { model.navigate("budgets") })
                SettingsRow("Demo mode", "Explore with random sample transactions. Your own data is left untouched.",
                    checked = demo, onCheckedChange = onDemo)
            }
        }

        if (transactions != null) {
            item { SettingsSection("Your data") }
            item { SettingsGroup { TransactionDataControls(transactions, demo || !allowDataTransfer, ledger, onDataMessage) } }
        }

        item { SettingsSection("Security") }
        item {
            var pinDialog by remember { mutableStateOf(false) }
            SettingsGroup {
                SettingsRow("App PIN",
                    if (s.pinSet) "Asked every time you open Notifly" else "Lock Notifly with a 6-digit PIN",
                    checked = s.pinSet, onCheckedChange = { pinDialog = true })
                if (biometricAvailable) SettingsRow("Unlock with biometrics",
                    if (s.pinSet) "Use your fingerprint or face instead of the PIN" else "Set a PIN first",
                    checked = s.biometric, onCheckedChange = if (s.pinSet) { value -> if (value) authenticateBiometric { model.biometric(true) } else model.biometric(false) } else null)
            }
            if (pinDialog && s.pinSet) PinDialog(onDismiss = { pinDialog = false }) { pinDialog = false; model.clearPin(it) }
            if (pinDialog && !s.pinSet) PinSetupScreen(onDismiss = { pinDialog = false }) { pinDialog = false; model.setPin(it) }
        }

        item { SettingsSection("Appearance") }
        item {
            SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                run {
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
        }

        item { SettingsSection("Capture") }
        item {
            SettingsGroup {
                val listening = permissionAvailable && connection == "Connected"
                SettingsRow(
                    "Notification access",
                    if (permissionAvailable) "On · Listener ${connection.lowercase()}" else "Off · Manual entry still works",
                    subtitleColor = if (listening) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    onClick = requestPermission,
                )
                SettingsRow("Allowed apps", "Choose which notifications Notifly reads", onClick = { model.navigate("allow-list") })
                SettingsRow("Finance apps", "Detect transfers between your bank and wallet apps", onClick = { model.navigate("finance-apps") })
            }
        }

        item { SettingsSection("Support & privacy") }
        item {
            SettingsGroup {
                SettingsRow("Help", "Guides for setting up capture", onClick = { uriHandler.openUri(HELP_URL) }, external = true)
                SettingsRow("Report a bug", "Tell us what went wrong", onClick = { uriHandler.openUri(BUG_REPORT_URL) }, external = true)
                SettingsRow(
                    "Send crash reports",
                    "Reports contain stack traces and device info only — never notification text.",
                    checked = s.crashReporting,
                    onCheckedChange = model::crashReporting,
                )
            }
        }

        item { SettingsSection("About") }
        item {
            SettingsGroup {
                SettingsRow("Version", versionName)
                SettingsRow("Release notes", "What changed in each version", onClick = { uriHandler.openUri(RELEASE_NOTES_URL) }, external = true)
                SettingsRow("Open source licenses", "Libraries Notifly is built with", onClick = { model.navigate("licenses") })
            }
        }

        if (isDebugBuild) {
            item { SettingsSection("Developer") }
            item {
                SettingsGroup {
                    SettingsRow("Notification log", "See what was captured and what couldn't be read", onClick = { model.navigate("log") })
                    SettingsRow("Replay onboarding", "Restart the first-run flow", onClick = model::restartOnboarding)
                    SettingsRow("Theme palettes", "Render every palette side by side", onClick = { model.navigate("themes") })
                    SettingsRow("Build", "debug")
                }
            }
        }
        item { BrandFooter() }
    }
}

@Composable
fun BudgetsScreen(model: BudgetsModel) {
    val budgets by model.state.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    // Keeps budgets for categories that were since dropped from the list reachable, so they can be removed.
    val typed by model.categories.collectAsState()
    val categories = (typed.filter { it.type == TransactionType.EXPENSE && (!it.archived || it.budgetMinor != null) }.map { it.name } + budgets.keys.sorted()).distinct()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text("Limits reset on the 1st of each month. Only confirmed expenses count toward them.",
                Modifier.padding(vertical = 8.dp))
        }
        item {
            SettingsGroup {
                categories.forEach { category ->
                    SettingsRow(category, budgets[category]?.let { "${money(it)} per month" } ?: "No budget",
                        onClick = { editing = category })
                }
            }
        }
    }
    editing?.let { category ->
        BudgetDialog(budgets[category], dismiss = { editing = null }, title = "$category budget",
            message = "How much do you plan to spend on $category each month?") { model.budget(category, it); editing = null }
    }
}

@Composable
fun LicensesScreen() {
    val libraries by produceLibraries { Res.readBytes("files/aboutlibraries.json").decodeToString() }
    LibrariesContainer(libraries, Modifier.fillMaxSize())
}

@Composable
fun AllowListScreen(model: AllowListModel, onFinish: (() -> Unit)? = null) {
    val s by model.state.collectAsState()
    var searchText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (onFinish != null) Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) { StepTrail(3) }
        Text(if (onFinish != null) "Only apps you select are read. Pick the bank and e-wallet apps that send you payment alerts."
            else if (s.finance) "Pick the bank and wallet apps you move money between. Matching in and out alerts become one transfer."
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
    }
    if (onFinish != null) Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(when (s.checked) { 0 -> "No apps selected yet"; 1 -> "1 app selected"; else -> "${s.checked} apps selected" },
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(onClick = onFinish, modifier = WideButton) { Text("Finish setup") }
        Text("Change your choices in Settings anytime.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}

/** Steps 1–3 of first run; step 4 is the app picker on `choose-apps`. Actions stay pinned below the scrolling copy. */
@Composable
fun OnboardingScreen(
    model: OnboardingModel,
    page: Int,
    requestPermission: () -> Unit,
    permissionAvailable: Boolean,
    batteryExempt: Boolean,
    requestBatteryExemption: () -> Unit,
) {
    val next: () -> Unit = { model.navigate(if (page < 2) "onboarding/${page + 1}" else "choose-apps") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StepTrail(page)
            Text(listOf("Stop typing your expenses", "One permission to grant", "Keep it running")[page],
                style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(listOf(
                "Choose your apps. We parse payment alerts on-device. You review and confirm every transaction.",
                "Notification access lets Notifly read alerts only from allowed apps. Raw notification text is never uploaded.",
                "Android can pause background apps to save power, and some phones do it aggressively. Turning that off for Notifly keeps captures arriving promptly.",
            )[page], style = MaterialTheme.typography.bodyLarge, color = muted)
            when (page) {
                0 -> {
                    SampleSlip()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(Res.drawable.symbol_lock), null, Modifier.size(20.dp), tint = muted)
                        Text("Your notifications stay on your device.", style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                }
                1 -> SetupStatus(Res.drawable.symbol_notifications, "Notification access", permissionAvailable,
                    if (permissionAvailable) "On. Alerts from the apps you choose can become drafts."
                    else "Off. Notifly can't read alerts yet; you can still add transactions manually.")
                else -> {
                    SetupStatus(Res.drawable.symbol_battery_android_full, "Background capture", batteryExempt,
                        if (batteryExempt) "Unrestricted. Captures arrive promptly."
                        else "Battery-optimised. Capture still works, but may be delayed on some phones.")
                    if (!batteryExempt) Text("In the list that opens, switch the filter to All apps, then pick Notifly.",
                        style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                page == 0 -> {
                    Button(onClick = next, modifier = WideButton) { Text("Get started") }
                    Text("No account needed.", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                page == 1 && !permissionAvailable -> {
                    Button(onClick = requestPermission, modifier = WideButton) { Text("Grant access") }
                    TextButton(onClick = next, modifier = WideButton) { Text("Skip — add manually") }
                }
                page == 2 && !batteryExempt -> {
                    Button(onClick = requestBatteryExemption, modifier = WideButton) { Text("Open battery settings") }
                    TextButton(onClick = next, modifier = WideButton) { Text("Skip for now") }
                }
                else -> Button(onClick = next, modifier = WideButton) { Text("Continue") }
            }
        }
    }
}

private val ONBOARDING_STEPS = listOf("Hello", "Access", "Background", "Apps")

/** The mark, "Step n of 4 · name", and four flat segments filled through [step]. */
@Composable
private fun StepTrail(step: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NotiflyMark(size = 28.dp)
            Text("Step ${step + 1} of 4 · ${ONBOARDING_STEPS[step]}", style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(ONBOARDING_STEPS.size) { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape)
                    .background(if (i <= step) scheme.primary else scheme.surfaceContainerHighest))
            }
        }
    }
}

/**
 * Shows the capture loop instead of describing it: an alert, the draft it becomes, then the one
 * ring-to-tick moment. Synthetic sample text, never real notification content.
 */
@Composable
private fun SampleSlip() {
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1600); confirmed = true }
    val amount = money(125_000)
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Example: a GCash alert for $amount becomes a Puregold draft that counts once you confirm it."
        }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(Res.drawable.symbol_notifications), null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                Column {
                    Text("GCash · Example", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text("You paid $amount to Puregold.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Puregold", style = MaterialTheme.typography.titleMedium)
                    Text(if (confirmed) "Groceries · Confirmed" else "Groceries · Needs review", style = MaterialTheme.typography.bodySmall,
                        color = if (confirmed) scheme.onSurfaceVariant else scheme.tertiary)
                }
                Text("−$amount", style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.accents.expense)
                Crossfade(confirmed, label = "sample-confirm") { StatusMark(confirmed = it) }
            }
        }
    }
}

/** A setup check stated in words and icon; announced politely when the user returns from system settings. */
@Composable
private fun SetupStatus(icon: DrawableResource, title: String, done: Boolean, detail: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (done) scheme.primaryContainer else scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(if (done) Res.drawable.symbol_check_circle else icon), null, Modifier.size(24.dp),
                tint = if (done) scheme.primary else scheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "A", "A and B", "A, B and 3 more". Expects at least one name. */
internal fun spokenList(names: List<String>): String = when (names.size) {
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> "${names[0]}, ${names[1]} and ${names.size - 2} more"
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
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (s.signup) "Create account" else "Welcome back", style = MaterialTheme.typography.displaySmall)
        if (demo) Text("Demo account flow — no account will be created.")
        OutlinedTextField(s.email, { model.edit(email = it) }, label = { Text("Email") }, singleLine = true,
            isError = s.emailError != null, supportingText = s.emailError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedSecureTextField(password, label = { Text("Password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = s.passwordError != null, supportingText = s.passwordError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus))
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Button(onClick = { model.submit() }, modifier = WideButton) { Text(if (s.signup) "Create account" else "Sign in") }
        TextButton(onClick = { model.edit(signup = !s.signup) }) { Text(if (s.signup) "I already have an account" else "Create account") }
        OutlinedButton(onClick = { model.startOffline() }, modifier = WideButton) { Text("Continue offline") }
    }
}
