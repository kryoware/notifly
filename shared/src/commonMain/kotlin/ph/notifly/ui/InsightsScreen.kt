@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ph.notifly.ui.theme.accents

@Composable
fun InsightsScreen(model: InsightsModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    var editingBudget by remember { mutableStateOf(false) }
    val w = s.selected ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                INSIGHT_WINDOWS.forEachIndexed { index, days ->
                    SegmentedButton(
                        selected = s.days == days,
                        onClick = { model.days(days) },
                        shape = SegmentedButtonDefaults.itemShape(index, INSIGHT_WINDOWS.size),
                        label = { Text("$days days") },
                    )
                }
            }
        }
        if (s.pending > 0) item {
            ListItem(
                onClick = { model.navigate("transactions") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                leadingContent = { Icon(painterResource(Res.drawable.symbol_pending_actions), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                content = { Text("${s.pending} awaiting review ${if (s.pending == 1) "isn't" else "aren't"} counted", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                supportingContent = { Text("Confirm them to include them here", color = MaterialTheme.colorScheme.onTertiaryContainer) },
            )
        }
        item { CashFlowCard(w) }
        item { DailySpendingCard(w) }
        item { PaceCard(s.windows, s.days, model::days) }
        s.month?.let { month -> item { BudgetCard(month, s.budget) { editingBudget = true } } }
        s.month?.takeIf { s.categoryBudgets.isNotEmpty() }?.let { month ->
            item { CategoryBudgetCard(month, s.categoryBudgets) { model.navigate("budgets") } }
        }
        item { CategoryCard(w) }
        if (w.largest.isNotEmpty()) {
            item { Text("Largest expenses · last ${w.days} days", style = MaterialTheme.typography.titleMedium) }
            items(w.largest, key = { it.id }) { t -> TransactionRow(t, appLabels, { model.navigate("edit/${t.id}") }) }
        }
    }
    if (editingBudget) BudgetDialog(s.budget, dismiss = { editingBudget = false }) { model.budget(it); editingBudget = false }
}

private fun signed(minor: Long) = (if (minor > 0) "+" else "") + money(minor)

private fun shortDate(date: LocalDate) = "${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.day}"

/** Display-only ratio for progress bars; money itself stays in Long. */
private fun ratio(part: Long, whole: Long) = if (whole <= 0L) 0f else (part.coerceIn(0L, whole) * 1000 / whole).toInt() / 1000f

/** Percent change against the previous window; [compared] names that window, or null for the terse form. */
@Composable
private fun Change(current: Long, previous: Long, compared: String?, lowerIsBetter: Boolean = true) {
    val change = percentChange(current, previous)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) = when {
        change == null && current == 0L -> (if (compared == null) "—" else "Nothing in either period") to neutral
        change == null -> (if (compared == null) "New" else "Nothing in the $compared") to neutral
        change == 0L -> (if (compared == null) "No change" else "Same as the $compared") to neutral
        else -> "${if (change > 0) "▲" else "▼"} ${abs(change)}%${compared?.let { " vs $it" }.orEmpty()}" to
            if ((change > 0) == lowerIsBetter) MaterialTheme.accents.expense else MaterialTheme.accents.income
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.semantics { contentDescription = text.replace("▲", "up").replace("▼", "down") })
}

@Composable
private fun CashFlowCard(w: WindowInsights) {
    val net = w.current.net
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Net cash flow · last ${w.days} days", style = MaterialTheme.typography.labelLarge)
            Text(signed(net), style = MaterialTheme.typography.headlineLarge, color = when {
                net > 0 -> MaterialTheme.accents.income
                net < 0 -> MaterialTheme.accents.expense
                else -> MaterialTheme.colorScheme.onSurface
            })
            Text("Previous ${w.days} days: ${signed(w.previous.net)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Money in", style = MaterialTheme.typography.labelMedium)
                    Text(money(w.current.income), style = MaterialTheme.typography.titleMedium)
                    Change(w.current.income, w.previous.income, null, lowerIsBetter = false)
                }
                Column(Modifier.weight(1f)) {
                    Text("Money out", style = MaterialTheme.typography.labelMedium)
                    Text(money(w.current.spent), style = MaterialTheme.typography.titleMedium)
                    Change(w.current.spent, w.previous.spent, null)
                }
            }
            Text("Compared with the previous ${w.days} days. Transfers between your accounts aren't counted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DailySpendingCard(w: WindowInsights) {
    var picked by remember(w.days, w.start) { mutableStateOf<Int?>(null) }
    val peak = w.daily.max()
    val detail = picked?.let { "${shortDate(w.start.plus(it, DateTimeUnit.DAY))}: ${money(w.daily[it])}" }
        ?: if (peak == 0L) "No spending in the last ${w.days} days"
        else "Average ${money(w.dailyAverage)}/day (dashed line) · peak ${money(peak)} on ${shortDate(w.start.plus(w.daily.indexOf(peak), DateTimeUnit.DAY))}"
    val bar = MaterialTheme.accents.expense
    val empty = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily spending", style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(128.dp)
                .pointerInput(w.days, w.start) {
                    detectTapGestures { tap ->
                        val index = (tap.x / size.width * w.days).toInt().coerceIn(0, w.days - 1)
                        picked = if (picked == index) null else index
                    }
                }
                .semantics { contentDescription = "Daily spending chart. $detail" }
            ) {
                val slot = size.width / w.days
                val barWidth = slot * 0.6f
                val scale = if (peak == 0L) 0f else size.height / peak
                val radius = CornerRadius(minOf(barWidth / 2, 4.dp.toPx()))
                w.daily.forEachIndexed { i, value ->
                    val height = maxOf(value * scale, 2.dp.toPx())
                    drawRoundRect(
                        color = when {
                            value == 0L -> empty
                            picked == null || picked == i -> bar
                            else -> bar.copy(alpha = 0.35f)
                        },
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
                if (peak > 0L) {
                    val y = size.height - w.dailyAverage * scale
                    drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shortDate(w.start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaceCard(windows: List<WindowInsights>, selected: Int, select: (Int) -> Unit) {
    val recent = windows.first()
    val baseline = windows.last()
    // Without history before the longest window its average is diluted by days that predate any data.
    val change = if (baseline.previous == CashFlow()) null else percentChange(recent.dailyAverage, baseline.dailyAverage)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spending pace", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    change == null -> "Average spending per day. A pace comparison appears once you have over ${baseline.days} days of history."
                    abs(change) < 5 -> "Your last ${recent.days} days match your ${baseline.days}-day daily average."
                    change > 0 -> "Your last ${recent.days} days cost $change% more per day than your ${baseline.days}-day average."
                    else -> "Your last ${recent.days} days cost ${-change}% less per day than your ${baseline.days}-day average."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            windows.forEach { w ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = w.days == selected, onClick = { select(w.days) }, role = Role.Tab)
                        .background(if (w.days == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Last ${w.days} days", style = MaterialTheme.typography.bodyLarge)
                        Text("${money(w.dailyAverage)}/day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(w.current.spent), style = MaterialTheme.typography.titleSmall)
                        Change(w.current.spent, w.previous.spent, "previous ${w.days} days")
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(m: MonthInsights, budget: Long?, edit: () -> Unit) {
    val spent = m.flow.spent
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = edit) { Text(if (budget == null) "Set budget" else "Edit") }
            }
            if (budget == null) {
                Text("You've spent ${money(spent)} so far this month.")
                Text("At this pace: ${money(m.projected)} by month end. Set a budget to see what's left per day.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val over = spent > budget
                val trendingOver = m.projected > budget
                LinearProgressIndicator(progress = { ratio(spent, budget) }, modifier = Modifier.fillMaxWidth(),
                    color = if (over || trendingOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${money(spent)} spent", style = MaterialTheme.typography.titleSmall)
                    Text("of ${money(budget)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (over) "Over budget by ${money(spent - budget)}."
                    else "${money(budget - spent)} left · about ${money(m.perDayLeft(budget))}/day for ${m.daysLeft} day${if (m.daysLeft == 1) "" else "s"}")
                Text(if (trendingOver) "At this pace: ${money(m.projected)} by month end, ${money(m.projected - budget)} over."
                    else "At this pace: ${money(m.projected)} by month end, within budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (trendingOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Money in this month: ${money(m.flow.income)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCard(w: WindowInsights) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                Text("Last ${w.days} days · change vs the ${w.days} days before", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.categories.isEmpty()) Text("No spending in the last ${w.days} days.")
            w.categories.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(money(c.spent), style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(progress = { ratio(c.spent, w.current.spent) }, modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.accents.expense)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.spent * 100 / w.current.spent}% of spending", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Change(c.spent, c.previous, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(m: MonthInsights, budgets: Map<String, Long>, manage: () -> Unit) {
    val rows = budgets.entries.map { (category, budget) -> Triple(category, m.categories[category] ?: 0L, budget) }
        .sortedByDescending { (_, spent, budget) -> spent * 1000 / budget }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category budgets · this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = manage) { Text("Manage") }
            }
            rows.forEach { (category, spent, budget) ->
                val over = spent > budget
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${money(spent)} of ${money(budget)}", style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(progress = { ratio(spent, budget) }, modifier = Modifier.fillMaxWidth(),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(if (over) "Over by ${money(spent - budget)}" else "${money(budget - spent)} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun BudgetDialog(
    current: Long?, dismiss: () -> Unit,
    title: String = "Monthly budget",
    message: String = "How much do you plan to spend each month? Transfers don't count toward it.",
    save: (Long?) -> Unit,
) {
    var text by remember { mutableStateOf(current?.let(::amountText).orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                OutlinedTextField(
                    value = text, onValueChange = { text = it; invalid = false },
                    label = { Text("Amount") }, prefix = { Text("₱") }, singleLine = true, isError = invalid,
                    supportingText = if (invalid) { { Text("Enter an amount above zero, with at most two decimal places.") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { parseAmountMinor(text)?.let(save) ?: run { invalid = true } }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { save(null) }) { Text("Remove") }
                TextButton(onClick = dismiss) { Text("Cancel") }
            }
        },
    )
}
