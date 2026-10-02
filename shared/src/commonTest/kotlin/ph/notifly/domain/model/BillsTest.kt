package ph.notifly.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

private val ZONE = TimeZone.UTC
private fun day(text: String) = LocalDate.parse(text)

private fun bill(
    startsOn: String, repeat: BillRepeat, id: Long = 1, name: String = "Meralco", amountMinor: Long = 241_000,
    settled: Int = 0, status: TransactionStatus = TransactionStatus.CONFIRMED,
) = Bill(id = id, name = name, amountMinor = amountMinor, startsOn = day(startsOn), repeat = repeat,
    settled = settled, status = status, createdAt = Instant.fromEpochMilliseconds(0))

private fun expense(id: Long, title: String, amountMinor: Long, on: String, status: TransactionStatus = TransactionStatus.CONFIRMED) =
    Transaction(id = id, title = title, amountMinor = amountMinor, type = TransactionType.EXPENSE, status = status,
        category = "Bills", occurredAt = day(on).atStartOfDayIn(ZONE), sourceApp = null, captureId = null, accountId = 1)

private fun payment(billId: Long, due: String, transactionId: Long?) =
    BillPayment(id = 1, billId = billId, dueOn = day(due), transactionId = transactionId, settledAt = Instant.fromEpochMilliseconds(0))

class BillsTest {
    @Test fun monthEndClampsWithoutDrift() {
        val b = bill("2026-01-31", BillRepeat.MONTHLY)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"), (0..4).map { b.occurrence(it).toString() })
    }

    @Test fun weeklyIsSevenDays() {
        val b = bill("2026-10-01", BillRepeat.WEEKLY)
        assertEquals(day("2026-10-08"), b.occurrence(1))
        assertEquals(day("2026-10-29"), b.occurrence(4))
    }

    @Test fun yearlyFromLeapDayKeepsAnchor() {
        val b = bill("2028-02-29", BillRepeat.YEARLY)
        assertEquals(day("2029-02-28"), b.occurrence(1))
        assertEquals(day("2032-02-29"), b.occurrence(4))
    }

    @Test fun nextDueFollowsSettledAndSkipped() {
        val b = bill("2026-10-15", BillRepeat.MONTHLY)
        assertEquals(day("2026-10-15"), b.nextDue)
        assertEquals(day("2026-11-15"), b.copy(settled = 1).nextDue)
        assertEquals(day("2026-12-15"), b.copy(settled = 2).nextDue)
    }

    @Test fun onceSettledHasNoNextDue() {
        val b = bill("2026-10-15", BillRepeat.ONCE)
        assertEquals(day("2026-10-15"), b.nextDue)
        assertNull(b.copy(settled = 1).nextDue)
    }

    @Test fun validateRejectsBlankNameAndNonPositiveAmount() {
        assertFailsWith<IllegalArgumentException> { bill("2026-10-15", BillRepeat.ONCE, name = " ").validate() }
        assertFailsWith<IllegalArgumentException> { bill("2026-10-15", BillRepeat.ONCE, amountMinor = 0).validate() }
        bill("2026-10-15", BillRepeat.ONCE).validate()
    }

    @Test fun occurrencesProjectRecurringAndMarkPaid() {
        val b = bill("2026-09-15", BillRepeat.MONTHLY, settled = 1)
        val rows = billOccurrences(listOf(b), listOf(payment(1, "2026-09-15", 7)), day("2026-09-01"), day("2026-11-30"))
        assertEquals(listOf("2026-09-15", "2026-10-15", "2026-11-15"), rows.map { it.dueOn.toString() })
        assertEquals(listOf(true, false, false), rows.map { it.paid })
    }

    @Test fun occurrencesOmitSkippedDraftsAndOutOfRange() {
        val skipped = bill("2026-09-15", BillRepeat.MONTHLY, settled = 1)
        val draft = bill("2026-10-20", BillRepeat.ONCE, id = 2, status = TransactionStatus.NEEDS_REVIEW)
        val once = bill("2026-12-01", BillRepeat.ONCE, id = 3)
        val rows = billOccurrences(listOf(skipped, draft, once), listOf(payment(1, "2026-09-15", null)), day("2026-09-01"), day("2026-10-31"))
        assertEquals(listOf("2026-10-15"), rows.map { it.dueOn.toString() })
    }

    @Test fun bucketsByNextDue() {
        val today = day("2026-10-10")
        val bills = listOf(
            bill("2026-10-08", BillRepeat.ONCE, id = 1, name = "Overdue"),
            bill("2026-10-16", BillRepeat.ONCE, id = 2, name = "Week end"),
            bill("2026-10-17", BillRepeat.ONCE, id = 3, name = "Later"),
            bill("2026-10-10", BillRepeat.ONCE, id = 4, name = "Today"),
            bill("2026-10-12", BillRepeat.ONCE, id = 5, name = "Done", settled = 1),
            bill("2026-10-12", BillRepeat.ONCE, id = 6, name = "Draft", status = TransactionStatus.NEEDS_REVIEW),
        )
        val result = upcoming(bills, today)
        assertEquals(listOf("Overdue"), result.overdue.map { it.bill.name })
        assertEquals(listOf("Today", "Week end"), result.thisWeek.map { it.bill.name })
        assertEquals(listOf("Later"), result.later.map { it.bill.name })
    }

    @Test fun candidatesRespectWindowAmountAndLinks() {
        val b = bill("2026-10-15", BillRepeat.MONTHLY)
        val due = day("2026-10-15")
        val rows = listOf(
            expense(1, "Groceries", 241_000, "2026-10-14"),
            expense(2, "Meralco online", 100_000, "2026-10-12"),
            expense(3, "Meralco old", 241_000, "2026-10-04"),
            expense(4, "Lunch", 15_000, "2026-10-14"),
            expense(5, "GCash", 241_000, "2026-10-18", TransactionStatus.NEEDS_REVIEW),
            expense(6, "GCash late", 241_000, "2026-10-19"),
            expense(7, "Linked", 241_000, "2026-10-15"),
        )
        val found = paymentCandidates(b, due, rows, listOf(payment(1, "2026-09-15", 7)), ZONE)
        assertEquals(listOf(1L, 5L, 2L), found.map { it.id })
    }

    @Test fun candidatesIgnoreIncomeAndCapAtThree() {
        val b = bill("2026-10-15", BillRepeat.MONTHLY)
        val rows = (1L..5L).map { expense(it, "Meralco", 241_000, "2026-10-15") } +
            expense(9, "Meralco refund", 241_000, "2026-10-15").copy(type = TransactionType.INCOME)
        assertEquals(3, paymentCandidates(b, day("2026-10-15"), rows, emptyList(), ZONE).size)
    }
}
