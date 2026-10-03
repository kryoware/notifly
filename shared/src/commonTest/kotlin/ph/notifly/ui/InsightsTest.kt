package ph.notifly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

class InsightsTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 9, 26)

    private fun row(
        day: String, amountMinor: Long, type: TransactionType = TransactionType.EXPENSE,
        category: String = "Food", status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(title = "[TEST] $category", amountMinor = amountMinor, type = type, status = status, category = category,
        occurredAt = LocalDate.parse(day).atTime(LocalTime(12, 0)).toInstant(zone), sourceApp = null, captureId = null, accountId = 1)

    private val rows = listOf(
        row("2026-09-26", 10_000),
        row("2026-09-20", 5_000, category = "Transport"),
        row("2026-09-19", 99_999),                                       // one day before the 7-day window
        row("2026-09-15", 20_000),
        row("2026-09-25", 300_000, TransactionType.INCOME, "Income"),
        row("2026-09-24", 50_000, TransactionType.TRANSFER, "Transfer"),
        row("2026-09-24", 70_000, status = TransactionStatus.NEEDS_REVIEW),
        row("2026-09-27", 1_000),                                        // future-dated
    )

    @Test fun chartSlicesFoldEverythingPastTheLimitIntoOneSlice() {
        val shares = listOf("Food" to 500L, "Bills" to 300L, "Other" to 100L, "Gifts" to 60L, "Fees" to 40L)
        assertEquals(shares.take(2) + ("3 more" to 200L), chartSlices(shares, 2))
        assertEquals(shares, chartSlices(shares, 5))
    }

    @Test fun transferFeesGetTheirOwnShareSoCategoriesSumToSpent() {
        val fee = row("2026-09-24", 50_000, TransactionType.TRANSFER, "Transfer").copy(feeMinor = 1_500)
        val w = windowInsights(rows + fee, today, 7, zone)
        assertEquals(CategorySpend(TRANSFER_FEES, 1_500, 0), w.categories.single { it.category == TRANSFER_FEES })
        assertEquals(w.current.spent, w.categories.sumOf { it.spent })
        val named = windowInsights(rows + fee + row("2026-09-25", 2_000, category = TRANSFER_FEES), today, 7, zone)
        assertEquals(CategorySpend(TRANSFER_FEES, 3_500, 0), named.categories.single { it.category == TRANSFER_FEES })
        val earlierFee = fee.copy(occurredAt = row("2026-09-16", 0).occurredAt)
        val compared = windowInsights(rows + earlierFee + row("2026-09-25", 2_000, category = TRANSFER_FEES), today, 7, zone)
        assertEquals(CategorySpend(TRANSFER_FEES, 2_000, 1_500), compared.categories.single { it.category == TRANSFER_FEES })
    }

    @Test fun windowCountsConfirmedCashFlowAndComparesToPreviousWindow() {
        val w = windowInsights(rows, today, 7, zone)
        assertEquals(LocalDate(2026, 9, 20), w.start)
        assertEquals(CashFlow(income = 300_000, spent = 15_000), w.current)
        assertEquals(CashFlow(spent = 119_999), w.previous)
        assertEquals(listOf(5_000L, 0, 0, 0, 0, 0, 10_000), w.daily)
        assertEquals(listOf(CategorySpend("Food", 10_000, 119_999), CategorySpend("Transport", 5_000, 0)), w.categories)
        assertEquals(listOf(10_000L, 5_000), w.largest.map { it.amountMinor })
        assertEquals(2_142L, w.dailyAverage)
    }

    @Test fun monthProjectsSpendingAndSplitsRemainingBudget() {
        val m = monthInsights(rows, today, zone)
        assertEquals(CashFlow(income = 300_000, spent = 134_999), m.flow)
        assertEquals(mapOf("Food" to 129_999L, "Transport" to 5_000L), m.categories)
        assertEquals(30, m.length)
        assertEquals(5, m.daysLeft)
        assertEquals(134_999L * 30 / 26, m.projected)
        assertEquals((200_000L - 134_999) / 5, m.perDayLeft(200_000))
        assertEquals(0L, m.perDayLeft(100_000))
        assertEquals(29, monthInsights(emptyList(), LocalDate(2028, 2, 1), zone).length)
    }

    @Test fun percentChangeNeedsABaseline() {
        assertNull(percentChange(500, 0))
        assertEquals(-50L, percentChange(50, 100))
        assertEquals(25L, percentChange(125, 100))
    }
}
