@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.*
import ph.notifly.ui.theme.accents
import ph.notifly.ui.theme.tabular
import kotlin.time.Instant

private val FAB_CLEARANCE = 88.dp
private val WideButton = Modifier.fillMaxWidth().heightIn(min = 56.dp)
private const val SWIPE_THRESHOLD = 0.5f

private fun monthTitle(month: LocalDate) = month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year
private fun longMonth(month: Month) = month.name.lowercase().replaceFirstChar { it.uppercase() }
private fun weekday(date: LocalDate) = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
private fun BillRepeat.label() = when (this) {
    BillRepeat.ONCE -> "One-time"; BillRepeat.WEEKLY -> "Weekly"; BillRepeat.MONTHLY -> "Monthly"; BillRepeat.YEARLY -> "Yearly"
}
private fun daysBetween(from: LocalDate, to: LocalDate) = (to.toEpochDays() - from.toEpochDays()).toInt()

/** Describes the due date relative to [today] in calendar days, including overdue dates. */
internal fun dueWords(due: LocalDate, today: LocalDate): String = when (val d = daysBetween(today, due)) {
    0 -> "Due today"
    1 -> "Due tomorrow"
    in 2..Int.MAX_VALUE -> "Due in $d days"
    -1 -> "Overdue by 1 day"
    else -> "Overdue by ${-d} days"
}

/**
 * Shows upcoming and calendar bills, review actions, and payment choices. Adding from the calendar
 * prefills the selected date; enabling reminders requests notification permission when needed.
 */
@Composable
fun BillsScreen(model: BillsModel, appLabels: Map<String, String> = emptyMap(), snackbar: SnackbarHostState? = null,
                demo: Boolean = false, notificationsAllowed: Boolean = true, requestNotifications: () -> Unit = {}) {
    val s by model.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var payingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedDay = selected?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: s.today
    val listState = rememberLazyListState()
    val paying = payingId?.let { id -> s.bills.find { it.id == id }?.let { b -> b.nextDue?.let { BillDue(b, it) } } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (demo) "Bills · Demo" else "Bills") }) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = {
            AddTransactionFab("Add bill", expanded = tab == 1 || !listState.canScrollBackward) {
                model.navigate(if (tab == 1) "bill/0?due=$selectedDay" else "bill/0")
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
          if (maxWidth >= WideWidth) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { UpcomingTab(model, s, appLabels, listState, notificationsAllowed, requestNotifications) { payingId = it.bill.id } }
            VerticalDivider()
            Box(Modifier.weight(1f)) { CalendarTab(model, s, appLabels, selectedDay, { selected = it.toString() }) { payingId = it.bill.id } }
          } else Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Upcoming", "Calendar").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            Crossfade(tab, label = "bills-tab") { current ->
                if (current == 0) UpcomingTab(model, s, appLabels, listState, notificationsAllowed, requestNotifications) { payingId = it.bill.id }
                else CalendarTab(model, s, appLabels, selectedDay, { selected = it.toString() }) { payingId = it.bill.id }
            }
        }
        }
    }
    paying?.let { due -> PaySheet(model, s, due, onDismiss = { payingId = null }) }
}

@Composable
private fun UpcomingTab(model: BillsModel, s: BillsState, appLabels: Map<String, String>, listState: androidx.compose.foundation.lazy.LazyListState,
                        notificationsAllowed: Boolean, requestNotifications: () -> Unit, pay: (BillDue) -> Unit) {
    val active = s.due.all
    val showPrompt = s.reminderDays == null && !s.promptDismissed && active.isNotEmpty()
    if (!s.loaded) return
    if (active.isEmpty() && s.detected.isEmpty()) {
        EmptyState("Nothing due.", "Add a bill, or let Notifly spot due notices from your allowed apps.", Modifier.fillMaxSize()) {
            FilledTonalButton(onClick = { model.navigate("bill/0") }) { Text("Add bill") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = FAB_CLEARANCE)) {
        if (active.isNotEmpty()) item { SummaryCard(s) }
        if (s.detected.isNotEmpty()) {
            item { SectionHeader("Detected · Needs review") }
            items(s.detected.size, key = { "detected:" + s.detected[it].id }) { i ->
                DetectedRow(s.detected[i], appLabels, s.hideAmounts, model)
            }
        }
        listOf("Overdue" to s.due.overdue, "This week" to s.due.thisWeek, "Later" to s.due.later).forEach { (title, rows) ->
            if (rows.isNotEmpty()) {
                item { SectionHeader(title) }
                items(rows.size, key = { "bill:" + rows[it].bill.id }) { i ->
                    val row = rows[i]
                    BillRow(row.bill.name, row.bill.amountMinor, row.dueOn, s.today, row.bill.repeat, s.hideAmounts,
                        open = { model.navigate("bill/${row.bill.id}") }, pay = { pay(row) })
                }
            }
        }
        if (showPrompt) item {
            Card(Modifier.padding(top = 16.dp).fillMaxWidth(), colors = brandCardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Get reminded before bills are due", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (!notificationsAllowed) requestNotifications(); model.turnOnReminders() }) { Text("Turn on") }
                        TextButton(onClick = model::dismissPrompt) { Text("Not now") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(s: BillsState) {
    val sum = s.summary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = brandCardColors()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Due in the next 30 days", style = MaterialTheme.typography.labelLarge)
            Text(splitMoney(sum.dueSoonMinor, LocalContentColor.current.copy(alpha = 0.55f)),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
                modifier = hiddenMoneyModifier(s.hideAmounts))
            val next = sum.next?.let { " · next ${it.bill.name} ${shortDate(it.dueOn)}" }.orEmpty()
            Text("${sum.dueSoonCount} ${if (sum.dueSoonCount == 1) "bill" else "bills"}$next", style = MaterialTheme.typography.bodySmall, color = muted)
            if (sum.overdueCount > 0) Row {
                Text(money(sum.overdueMinor), style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.tertiary,
                    modifier = hiddenMoneyModifier(s.hideAmounts))
                Text(" overdue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun DateStamp(date: LocalDate) {
    Column(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(date.month.name.take(3).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date.day.toString(), style = MaterialTheme.typography.titleMedium.tabular())
    }
}

/** One bill occurrence. [pay] shows the ring button; [paidText] marks the row paid instead. */
@Composable
private fun BillRow(name: String, amountMinor: Long, due: LocalDate, today: LocalDate, repeat: BillRepeat, hidden: Boolean,
                    open: () -> Unit, pay: (() -> Unit)? = null, paidText: String? = null) {
    val overdue = paidText == null && due < today
    val words = paidText ?: dueWords(due, today)
    ListItem(
        onClick = open,
        modifier = Modifier.semantics {
            contentDescription = listOfNotNull(name, if (hidden) null else money(amountMinor), words).joinToString(", ")
        },
        leadingContent = { DateStamp(due) },
        supportingContent = {
            Text(if (paidText != null) words else repeat.label() + " · " + words, style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (paidText == null) Text(money(amountMinor), style = MaterialTheme.typography.titleMedium.tabular(), modifier = hiddenMoneyModifier(hidden))
                if (pay != null) IconTooltip("Mark $name paid") {
                    IconButton(onClick = pay, modifier = Modifier.size(48.dp).semantics { contentDescription = "Mark $name paid" }) { StatusMark(confirmed = false) }
                } else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StatusMark(confirmed = true) }
            }
        },
        content = { Text(name, style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
private fun DetectedRow(bill: Bill, appLabels: Map<String, String>, hidden: Boolean, model: BillsModel) {
    val source = bill.sourceApp?.let { appLabels[it] ?: it } ?: "Notifly"
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> { model.confirm(bill); true }
            SwipeToDismissBoxValue.EndToStart -> { model.dismiss(bill); true }
            SwipeToDismissBoxValue.Settled -> false
        }
    }, positionalThreshold = { it * SWIPE_THRESHOLD })
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Confirm ${bill.name} bill") { model.confirm(bill); true },
                CustomAccessibilityAction("Dismiss ${bill.name} bill") { model.dismiss(bill); true },
            )
        },
        backgroundContent = {
            val confirm = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                .background(if (confirm) MaterialTheme.accents.confirmed else MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 24.dp),
                contentAlignment = if (confirm) Alignment.CenterStart else Alignment.CenterEnd) {
                Icon(painterResource(if (confirm) Res.drawable.symbol_check else Res.drawable.symbol_clear), null,
                    tint = if (confirm) MaterialTheme.accents.onConfirmed else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        ListItem(
            onClick = { model.navigate("bill/${bill.id}") },
            leadingContent = { AppIcon(bill.sourceApp, source) },
            supportingContent = { Text("From $source · Needs review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(money(bill.amountMinor), style = MaterialTheme.typography.titleMedium.tabular(), modifier = hiddenMoneyModifier(hidden))
                    IconTooltip("Confirm $source bill") {
                        IconButton(onClick = { model.confirm(bill) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Confirm $source bill" }) {
                            StatusMark(confirmed = false)
                        }
                    }
                }
            },
            content = { Text(bill.name, style = MaterialTheme.typography.titleMedium) },
        )
    }
}

/** Shows months from 12 before through 24 after the current month, with totals and the selected day's occurrences. */
@Composable
private fun CalendarTab(model: BillsModel, s: BillsState, appLabels: Map<String, String>, selected: LocalDate,
                        select: (LocalDate) -> Unit, pay: (BillDue) -> Unit) {
    val firstOfToday = LocalDate(s.today.year, s.today.month, 1)
    val pager = rememberPagerState(initialPage = 12) { 37 }
    val scope = rememberCoroutineScope()
    fun monthAt(page: Int) = firstOfToday.plus(page - 12, DateTimeUnit.MONTH)
    val month = monthAt(pager.currentPage)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle(month), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (pager.currentPage != 12) TextButton(onClick = { scope.launch { pager.animateScrollToPage(12) }; select(s.today) }) { Text("Today") }
            IconTooltip("Previous month") { IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) {
                Icon(painterResource(Res.drawable.symbol_chevron_left), "Previous month", modifier = mirroredIconModifier()) } }
            IconTooltip("Next month") { IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(36)) } }) {
                Icon(painterResource(Res.drawable.symbol_chevron_right), "Next month", modifier = mirroredIconModifier()) } }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth()) { page -> MonthGrid(monthAt(page), s, selected, select) }
        val monthStart = month
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val inMonth = billOccurrences(s.bills, s.payments, monthStart, monthEnd)
        val dueTotal = inMonth.filter { !it.paid }.sumOf { it.bill.amountMinor }
        val paidTotal = inMonth.filter { it.paid }.sumOf { occ -> s.transactions.find { it.id == occ.payment?.transactionId }?.amountMinor ?: occ.bill.amountMinor }
        Row(Modifier.padding(top = 8.dp).then(hiddenMoneyModifier(s.hideAmounts))) {
            Text("${money(dueTotal)} due · ${money(paidTotal)} paid", style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionHeader("${weekday(selected)}, ${shortDate(selected)}")
        val dayRows = billOccurrences(s.bills, s.payments, selected, selected)
        if (dayRows.isEmpty()) {
            Text("Nothing due on ${shortDate(selected)}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp))
            TextButton(onClick = { model.navigate("bill/0?due=$selected") }) { Text("Add bill on this day") }
        } else dayRows.forEach { occ ->
            val tx = occ.payment?.transactionId?.let { id -> s.transactions.find { it.id == id } }
            val paidText = if (occ.paid) listOfNotNull("Paid", tx?.let { appLabels[it.sourceApp] ?: s.accountNames[it.accountId] },
                tx?.let { if (s.hideAmounts) null else "−" + money(it.amountMinor) }).joinToString(" · ") else null
            BillRow(occ.bill.name, occ.bill.amountMinor, occ.dueOn, s.today, occ.bill.repeat, s.hideAmounts,
                open = { model.navigate("bill/${occ.bill.id}") },
                pay = if (occ.paid) null else { { pay(BillDue(occ.bill, occ.dueOn)) } }, paidText = paidText)
        }
        Spacer(Modifier.height(FAB_CLEARANCE))
    }
}

/** Shows six Sunday-first weeks starting with the week containing [month], which must be the first day of a month. */
@Composable
private fun MonthGrid(month: LocalDate, s: BillsState, selected: LocalDate, select: (LocalDate) -> Unit) {
    val offset = month.dayOfWeek.isoDayNumber % 7
    val gridStart = month.minus(offset, DateTimeUnit.DAY)
    val rows = billOccurrences(s.bills, s.payments, gridStart, gridStart.plus(41, DateTimeUnit.DAY)).groupBy { it.dueOn }
    Column(Modifier.fillMaxWidth()) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val date = gridStart.plus(week * 7 + col, DateTimeUnit.DAY)
                    DayCell(date, date.month == month.month, date == s.today, date == selected, rows[date].orEmpty(), Modifier.weight(1f)) { select(date) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, today: Boolean, selected: Boolean, items: List<BillOccurrence>, modifier: Modifier, onClick: () -> Unit) {
    val unpaid = items.count { !it.paid }
    val paid = items.count { it.paid }
    val description = buildString {
        append("${longMonth(date.month)} ${date.day}, ${date.year}")
        if (unpaid > 0) append(", $unpaid ${if (unpaid == 1) "bill" else "bills"} due")
        if (paid > 0) append(", $paid paid")
    }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.heightIn(min = 48.dp).clickable(onClick = onClick).semantics { contentDescription = description; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(28.dp).background(if (selected) scheme.primaryContainer else scheme.surface, CircleShape), contentAlignment = Alignment.Center) {
            Text(date.day.toString(), style = MaterialTheme.typography.bodyMedium.tabular(),
                color = when { today -> scheme.primary; inMonth -> scheme.onSurface; else -> scheme.onSurface.copy(alpha = 0.38f) },
                fontWeight = if (today) FontWeight.SemiBold else null)
        }
        Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            items.sortedBy { it.paid }.take(3).forEach { occ ->
                if (occ.paid) Box(Modifier.size(7.dp).background(MaterialTheme.accents.confirmed, CircleShape))
                else Box(Modifier.size(7.dp).border(1.5.dp, scheme.tertiary, CircleShape))
            }
            if (items.size > 3) Text("+${items.size - 3}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Offers matching transactions to link, a new payment to record, and skipping for recurring bills.
 * Linking an expense awaiting review also confirms it; selecting an action dismisses the sheet.
 */
@Composable
private fun PaySheet(model: BillsModel, s: BillsState, due: BillDue, onDismiss: () -> Unit) {
    val bill = due.bill
    val candidates = remember(s.transactions, s.payments, bill, due.dueOn) { paymentCandidates(bill, due.dueOn, s.transactions, s.payments) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mark ${bill.name} paid", style = MaterialTheme.typography.titleLarge)
            Text("${money(bill.amountMinor)} · due ${shortDate(due.dueOn)}", style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = hiddenMoneyModifier(s.hideAmounts))
            if (candidates.isNotEmpty()) {
                SectionHeader("Matching payments")
                candidates.forEach { t ->
                    val review = t.status == TransactionStatus.NEEDS_REVIEW
                    ListItem(
                        leadingContent = { StatusMark(confirmed = !review) },
                        supportingContent = { Text(shortDate(t.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date) + if (review) " · Needs review" else "",
                            style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { TextButton(onClick = { model.link(bill, due.dueOn, t); onDismiss() }) { Text(if (review) "Confirm and link" else "Link") } },
                        content = { Column {
                            Text(t.title, style = MaterialTheme.typography.titleMedium)
                            Text("−" + money(t.amountMinor), style = MaterialTheme.typography.bodySmall.tabular(), modifier = hiddenMoneyModifier(s.hideAmounts))
                        } },
                    )
                }
            }
            Button(onClick = { model.navigate("pay-bill/${bill.id}"); onDismiss() }, modifier = WideButton) { Text("Record payment") }
            if (bill.repeat != BillRepeat.ONCE) TextButton(onClick = { model.skip(bill, due.dueOn); onDismiss() }, Modifier.fillMaxWidth()) { Text("Skip this one") }
        }
    }
}

/**
 * Edits bill details and offers confirmation for detected bills; other existing bills can be deleted.
 * The date picker reads and writes UTC calendar dates. [appLabels] maps source packages to display names.
 */
@Composable
fun BillEditorScreen(model: BillEditorModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    var showDate by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val reviewing = s.reviewing
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)) {
        if (s.error != null && !s.ready) item { Text(s.error!!, color = MaterialTheme.colorScheme.error) }
        if (reviewing) item {
            val source = s.original?.sourceApp?.let { appLabels[it] ?: it } ?: "a"
            val on = s.original?.createdAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.let { shortDate(it) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusMark(confirmed = false, size = 16.dp)
                    Text("Detected from ${if (source == "a") "a" else "a $source"} notification${on?.let { " on $it" }.orEmpty()}. Check the amount and due date.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        item { OutlinedTextField(s.name, { model.edit(name = it) }, label = { Text("Name") }, singleLine = true, isError = s.nameError != null,
            supportingText = s.nameError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(s.amount, { model.edit(amount = it) }, label = { Text("Amount") }, prefix = { Text("₱") }, singleLine = true,
            isError = s.amountError != null, supportingText = s.amountError?.let { { Text(it) } }, placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) }
        item {
            OutlinedTextField(s.due.toString(), {}, readOnly = true, label = { Text("Due date") }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconTooltip("Choose date") { IconButton(onClick = { showDate = true }) {
                    Icon(painterResource(Res.drawable.symbol_calendar_today), "Choose date") } } })
        }
        item {
            Text("Repeats", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState())) {
                BillRepeat.entries.forEachIndexed { i, r ->
                    SegmentedButton(selected = s.repeat == r, onClick = { model.edit(repeat = r) },
                        shape = SegmentedButtonDefaults.itemShape(i, BillRepeat.entries.size), label = { Text(if (r == BillRepeat.ONCE) "Once" else r.label()) })
                }
            }
        }
        item { CategoryField(s.category, s.categories) { model.edit(category = it) } }
        item {
            ChoiceField("Pay from account", s.accounts.find { it.id == s.accountId }?.name ?: "Any account", listOf<Account?>(null) + s.accounts,
                label = { it?.name ?: "Any account" }) { model.edit(accountId = it?.id) }
        }
        item {
            Button(onClick = { if (reviewing) model.confirm() else model.save() }, enabled = s.ready && !s.saving, modifier = WideButton,
                colors = if (reviewing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.accents.confirmed, contentColor = MaterialTheme.accents.onConfirmed)
                else ButtonDefaults.buttonColors()) {
                if (reviewing) Icon(painterResource(Res.drawable.symbol_check), null, Modifier.padding(end = 8.dp).size(18.dp))
                Text(if (reviewing) "Confirm bill" else "Save bill")
            }
        }
        if (reviewing) item { TextButton(onClick = model::dismiss) { Text("Dismiss") } }
        else if (s.original != null) item { TextButton(onClick = { delete = true }) { Text("Delete bill", color = MaterialTheme.colorScheme.error) } }
    }
    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = s.due.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                pickerState.selectedDateMillis?.let { ms -> model.edit(due = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date) }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete bill?") },
        text = { Text("Payments already recorded stay in your transactions.") },
        confirmButton = { TextButton(onClick = { delete = false; model.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}

/** Home line: the next bill, or detections waiting for review. Never merged into the balance. */
@Composable
internal fun NextBillLine(next: NextBill?, detected: Int, hidden: Boolean, open: () -> Unit) {
    if (next == null && detected == 0) return
    val text = if (detected > 0) "$detected detected ${if (detected == 1) "bill" else "bills"} to review"
    else next!!.let { "Next bill · ${it.name} ${if (hidden) "" else money(it.amountMinor) + " "}· " + when {
        it.days < 0 -> "Overdue by ${-it.days} ${if (it.days == -1) "day" else "days"}"
        it.days == 0 -> "today"; it.days == 1 -> "tomorrow"; else -> "in ${it.days} days" } }
    ListItem(onClick = open, modifier = Modifier.padding(top = 8.dp).clip(MaterialTheme.shapes.large),
        leadingContent = { if (detected > 0) StatusMark(confirmed = false) else Icon(painterResource(Res.drawable.symbol_event_upcoming), null) },
        trailingContent = { Icon(painterResource(Res.drawable.symbol_keyboard_arrow_right), null, modifier = mirroredIconModifier()) },
        content = { Text(text, style = MaterialTheme.typography.bodyMedium, modifier = if (detected > 0) Modifier else hiddenMoneyModifier(hidden)) })
}
