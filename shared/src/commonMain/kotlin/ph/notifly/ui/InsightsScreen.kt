@file:OptIn(ExperimentalMaterial3Api::class)

package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.pie.DefaultSlice
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import ph.notifly.ui.theme.accents
import ph.notifly.ui.theme.tabular
import ph.notifly.ui.theme.Space
import ph.notifly.ui.theme.balance

/**
 * Shows insights for the selected day window, followed by budgets for the current month.
 * Renders nothing until the selected window is available. Selection and navigation, including to
 * budget setup, go through [model]; [appLabels] maps source package names to app display labels.
 */
@Composable
fun InsightsScreen(model: InsightsModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    val w = s.selected ?: return
    val full = StaggeredGridItemSpan.FullLine
    LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(360.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.lg),
        verticalItemSpacing = Space.md, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        item(span = full) {
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
        if (s.pending > 0) item(span = full) {
            ListItem(
                onClick = { model.navigate("transactions") },
                modifier = Modifier.clip(MaterialTheme.shapes.large),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                leadingContent = { StatusMark(confirmed = false) },
                content = { Text("${s.pending} awaiting review ${if (s.pending == 1) "isn't" else "aren't"} counted", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                supportingContent = { Text("Confirm them to include them here", color = MaterialTheme.colorScheme.onTertiaryContainer) },
            )
        }
        item { CashFlowCard(w) }
        item { DailySpendingCard(w) }
        item { PaceCard(s.windows, s.days, model::days) }
        item { CategoryCard(w) }
        if (s.accounts.isNotEmpty()) item { AccountsCard(s.accounts) }
        if (w.largest.isNotEmpty()) {
            item(span = full) { SectionHeader("Largest expenses · last ${w.days} days") }
            items(w.largest, key = { it.id }, span = { full }) { t -> TransactionRow(t, appLabels, { model.navigate("edit/${t.id}") }) }
        }
        s.month?.let { month ->
            item(span = full) { SectionHeader("This month") }
            item { BudgetCard(month, s.budget) { model.navigate("budgets") } }
            if (s.categoryBudgets.isNotEmpty()) item { CategoryBudgetCard(month, s.categoryBudgets) { model.navigate("budgets") } }
        }
    }
}

internal fun shortDate(date: LocalDate) = "${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.day}"

/** Display-only ratio for progress bars; money itself stays in Long. */
internal fun ratio(part: Long, whole: Long) = if (whole <= 0L) 0f else
    (part.coerceIn(0L, whole).toULong() * 1000UL / whole.toULong()).toInt() / 1000f

/** The brand's flat 6dp bar: no gap or stop dot, so a full budget reads as one solid line. */
@Composable
internal fun Meter(progress: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth().height(6.dp), color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {})
}

/**
 * Percent change against the previous window; [compared] names that window, or null for the terse form.
 * A zero previous value shows a neutral empty/new label instead of a percentage.
 * [lowerIsBetter] makes decreases favorable; false makes increases favorable.
 */
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
    Text(text, style = MaterialTheme.typography.bodySmall.tabular(), color = color,
        modifier = Modifier.semantics { contentDescription = text.replace("▲", "up").replace("▼", "down") })
}

/** Shows signed net cash flow, income, and spending against the preceding equally sized window. */
@Composable
private fun CashFlowCard(w: WindowInsights) {
    val net = w.current.net
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Net cash flow · last ${w.days} days", style = MaterialTheme.typography.titleMedium)
            val color = when {
                net > 0 -> MaterialTheme.accents.income
                net < 0 -> MaterialTheme.accents.expense
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(AnnotatedString(if (net > 0) "+" else "") + splitMoney(net, color.copy(alpha = 0.55f)), color = color,
                style = MaterialTheme.typography.balance)
            Text("Previous ${w.days} days: ${signedMoney(w.previous.net)}", style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                Column(Modifier.weight(1f)) {
                    Text("Money in", style = MaterialTheme.typography.labelMedium)
                    Text(money(w.current.income), style = MaterialTheme.typography.titleMedium.tabular())
                    Change(w.current.income, w.previous.income, null, lowerIsBetter = false)
                }
                Column(Modifier.weight(1f)) {
                    Text("Money out", style = MaterialTheme.typography.labelMedium)
                    Text(money(w.current.spent), style = MaterialTheme.typography.titleMedium.tabular())
                    Change(w.current.spent, w.previous.spent, null)
                }
            }
            Text("Compared with the previous ${w.days} days. Transfers between your accounts aren't counted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm))
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
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val guide = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("Daily spending", style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * Shows spending totals and daily averages for [windows], ordered shortest to longest by the caller.
 * [selected] is a day count; tapping a row passes its day count to [select]. The first and last
 * averages are compared only when the last window has prior cash flow and a nonzero daily average.
 *
 * @throws NoSuchElementException if [windows] is empty.
 * @throws ArithmeticException if a window has zero days.
 */
@Composable
private fun PaceCard(windows: List<WindowInsights>, selected: Int, select: (Int) -> Unit) {
    val recent = windows.first()
    val baseline = windows.last()
    // Without history before the longest window its average is diluted by days that predate any data.
    val change = if (baseline.previous == CashFlow()) null else percentChange(recent.dailyAverage, baseline.dailyAverage)
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(top = Space.xl, bottom = 10.dp)) {
            Column(Modifier.padding(start = Space.xl, end = Space.xl, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Spending pace", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    change == null -> "Average spending per day. A pace comparison appears once you have over ${baseline.days} days of history."
                    abs(change) < 5 -> "Your last ${recent.days} days match your ${baseline.days}-day daily average."
                    change > 0 -> "Your last ${recent.days} days cost $change% more per day than your ${baseline.days}-day average."
                    else -> "Your last ${recent.days} days cost ${-change}% less per day than your ${baseline.days}-day average."
                }, style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            windows.forEach { w ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = w.days == selected, onClick = { select(w.days) }, role = Role.Tab)
                        .background(if (w.days == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f))
                        .padding(horizontal = Space.xl, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Last ${w.days} days", style = MaterialTheme.typography.bodyLarge)
                        Text("${money(w.dailyAverage)}/day", style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(w.current.spent), style = MaterialTheme.typography.titleSmall.tabular())
                        Change(w.current.spent, w.previous.spent, "previous ${w.days} days")
                    }
                }
            }
        }
    }
}

/**
 * Shows month-to-date spending and its month-end projection against [budget], in centavos.
 * A null budget shows a setup prompt; otherwise shows the remainder or overspend and daily allowance,
 * including today. The budget action invokes [edit].
 *
 * @throws ArithmeticException if [m] has a zero day, or zero days left when computing the allowance.
 */
@Composable
private fun BudgetCard(m: MonthInsights, budget: Long?, edit: () -> Unit) {
    val spent = m.flow.spent
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = edit) { Text(if (budget == null) "Set budget" else "Edit") }
            }
            if (budget == null) {
                Text("You've spent ${money(spent)} so far this month.", style = LocalTextStyle.current.tabular())
                Text("At this pace: ${money(m.projected)} by month end. Set a budget to see what's left per day.",
                    style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val over = spent > budget
                val trendingOver = m.projected > budget
                Meter(ratio(spent, budget), if (over || trendingOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${money(spent)} spent", style = MaterialTheme.typography.titleSmall.tabular())
                    Text("of ${money(budget)}", style = LocalTextStyle.current.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (over) "Over budget by ${money(spent - budget)}."
                    else "${money(budget - spent)} left · about ${money(m.perDayLeft(budget))}/day for ${m.daysLeft} day${if (m.daysLeft == 1) "" else "s"}",
                    style = LocalTextStyle.current.tabular())
                Text(if (trendingOver) "At this pace: ${money(m.projected)} by month end, ${money(m.projected - budget)} over."
                    else "At this pace: ${money(m.projected)} by month end, within budget.",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (trendingOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Money in this month: ${money(m.flow.income)}", style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCard(w: WindowInsights) {
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                Text("Last ${w.days} days · change vs the ${w.days} days before", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.categories.isEmpty()) Text("No spending in the last ${w.days} days.")
            if (w.categories.size > 1) ShareDonut(w.categories.map { it.category to it.spent }, "spent", "Spending by category")
            w.categories.forEachIndexed { i, c ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(money(c.spent), style = MaterialTheme.typography.titleSmall.tabular())
                    }
                    Meter(ratio(c.spent, w.current.spent), sliceColor(i))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.spent * 100 / w.current.spent}% of spending", style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Change(c.spent, c.previous, null)
                    }
                }
            }
        }
    }
}

/**
 * Splits positive balances, largest first, into shares of their total. Card debt and overdrafts
 * can't be a slice, so their sum is noted under the rows instead.
 */
@Composable
private fun AccountsCard(balances: List<AccountBalance>) {
    val funded = balances.filter { it.netValue > 0 }.sortedByDescending { it.netValue }
    val total = funded.sumOf { it.netValue }
    val owing = balances.filter { it.netValue < 0 }
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Balance by account", style = MaterialTheme.typography.titleMedium)
                Text("Where your confirmed money sits today", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (funded.isEmpty()) Text("No account has a positive balance.")
            if (funded.size > 1) ShareDonut(funded.map { it.account.name to it.netValue }, "in accounts", "Balance by account")
            funded.forEachIndexed { i, a ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(money(a.netValue), style = MaterialTheme.typography.titleSmall.tabular())
                    }
                    Meter(ratio(a.netValue, total), sliceColor(i))
                    Text("${a.netValue * 100 / total}% of balances${if (a.account.archived) " · Archived" else ""}", style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (owing.isNotEmpty()) Text("Not shown: ${money(owing.sumOf { -it.netValue })} owed on " +
                if (owing.size == 1) owing.single().account.name else "${owing.size} accounts",
                style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ramp step for the [index]th largest share; shares past the ramp fold into one neutral slice. */
@Composable
private fun sliceColor(index: Int) = MaterialTheme.accents.chart.getOrElse(index) { MaterialTheme.colorScheme.outline }

/**
 * Part-to-whole donut for [shares], largest first, totalled in the hole above [caption]. Tapping a
 * slice shows that slice in the hole instead. The rows after it are its legend and table view, so
 * the ring carries no labels of its own.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun ShareDonut(shares: List<Pair<String, Long>>, caption: String, chart: String) {
    val slices = chartSlices(shares, MaterialTheme.accents.chart.size)
    val total = slices.sumOf { it.second }
    var picked by remember(slices) { mutableStateOf<Int?>(null) }
    val colors = slices.indices.map { sliceColor(it) }
    val (label, amount) = picked?.let { slices[it] } ?: (caption to total)
    val values = slices.map { ratio(it.second, total) }
    PieChart(
        values = values,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "$chart chart. " + slices.joinToString { (name, value) -> "$name ${value * 100 / total}%" }
        },
        slice = { i ->
            // The gap comes off both ends, so thin slices get at most a quarter of their sweep (×360°/4) each side.
            DefaultSlice(if (picked == null || picked == i) colors[i] else colors[i].copy(alpha = 0.35f), gap = minOf(1f, values[i] * 90f),
                clickable = true, onClick = { picked = if (picked == i) null else i })
        },
        labelConnector = {},
        holeSize = 0.7f,
        holeContent = { padding ->
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(money(amount), style = MaterialTheme.typography.titleSmall.tabular(), maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        maxPieDiameter = 176.dp,
        forceCenteredPie = true,
        pieAnimationSpec = KoalaPlotTheme.animationSpec,
    )
}

/**
 * Shows monthly category budgets in descending spending-to-budget ratio order, with remaining or
 * overspent amounts. [budgets] maps category names to limits in centavos; missing spending counts
 * as zero. The Manage action invokes [manage].
 *
 * @throws ArithmeticException if a zero budget is used when ordering categories.
 */
@Composable
private fun CategoryBudgetCard(m: MonthInsights, budgets: Map<String, Long>, manage: () -> Unit) {
    val rows = budgets.entries.map { (category, budget) -> Triple(category, m.categories[category] ?: 0L, budget) }
        .sortedByDescending { (_, spent, budget) -> spent * 1000 / budget }
    Card(Modifier.fillMaxWidth(), colors = brandCardColors()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category budgets · this month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = manage) { Text("Manage") }
            }
            rows.forEach { (category, spent, budget) ->
                val over = spent > budget
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${money(spent)} of ${money(budget)}", style = MaterialTheme.typography.titleSmall.tabular())
                    }
                    Meter(ratio(spent, budget), if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(if (over) "Over by ${money(spent - budget)}" else "${money(budget - spent)} left",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
