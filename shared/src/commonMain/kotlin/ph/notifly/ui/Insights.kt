package ph.notifly.ui

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

val INSIGHT_WINDOWS = listOf(7, 15, 30)

/** Transfers move money between the user's own accounts, so they are neither money in nor money out. */
data class CashFlow(val income: Long = 0L, val spent: Long = 0L) {
    val net get() = income - spent
}

data class CategorySpend(val category: String, val spent: Long, val previous: Long)

/** The last [days] calendar days including today, set against the [days] days before them. */
data class WindowInsights(
    val days: Int,
    val start: LocalDate,
    val current: CashFlow,
    val previous: CashFlow,
    /** Spending per day, oldest first; always [days] entries. */
    val daily: List<Long>,
    val categories: List<CategorySpend>,
    val largest: List<Transaction>,
) {
    val dailyAverage get() = current.spent / days
}

data class MonthInsights(val flow: CashFlow, val day: Int, val length: Int) {
    val daysLeft get() = length - day + 1
    /** Straight-line projection of month-to-date spending. */
    val projected get() = flow.spent * length / day
    /** What can still be spent per day, today included, without exceeding [budget]. */
    fun perDayLeft(budget: Long) = (budget - flow.spent).coerceAtLeast(0L) / daysLeft
}

/** Whole-percent change, or null when there is nothing to compare against. */
fun percentChange(current: Long, previous: Long): Long? =
    if (previous == 0L) null else (current - previous) * 100 / previous

private fun cashFlow(rows: List<Transaction>) = CashFlow(
    rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
    rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor },
)

/** NEEDS_REVIEW rows never count: insights describe the same money as the headline balance. */
private fun List<Transaction>.confirmedByDate(zone: TimeZone) = filter { it.status == TransactionStatus.CONFIRMED }
    .map { it.occurredAt.toLocalDateTime(zone).date to it }

fun windowInsights(rows: List<Transaction>, today: LocalDate, days: Int, zone: TimeZone): WindowInsights {
    val start = today.minus(days - 1, DateTimeUnit.DAY)
    val previousStart = start.minus(days, DateTimeUnit.DAY)
    val dated = rows.confirmedByDate(zone)
    val current = dated.filter { (date, _) -> date in start..today }
    val previous = dated.filter { (date, _) -> date >= previousStart && date < start }.map { it.second }
    val expenses = current.filter { it.second.type == TransactionType.EXPENSE }
    val byDay = expenses.groupBy({ it.first }, { it.second.amountMinor }).mapValues { it.value.sum() }
    val previousByCategory = previous.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
    return WindowInsights(
        days = days,
        start = start,
        current = cashFlow(current.map { it.second }),
        previous = cashFlow(previous),
        daily = (0 until days).map { byDay[start.plus(it, DateTimeUnit.DAY)] ?: 0L },
        categories = expenses.groupBy { it.second.category }
            .map { (category, rows) -> CategorySpend(category, rows.sumOf { it.second.amountMinor }, previousByCategory[category] ?: 0L) }
            .sortedByDescending { it.spent },
        largest = expenses.map { it.second }.sortedByDescending { it.amountMinor }.take(3),
    )
}

fun monthInsights(rows: List<Transaction>, today: LocalDate, zone: TimeZone): MonthInsights {
    val first = LocalDate(today.year, today.month, 1)
    val length = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    val month = rows.confirmedByDate(zone).filter { (date, _) -> date in first..today }.map { it.second }
    return MonthInsights(cashFlow(month), today.day, length)
}
