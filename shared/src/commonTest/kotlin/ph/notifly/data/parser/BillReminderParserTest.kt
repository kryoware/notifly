package ph.notifly.data.parser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BillReminderParserTest {
    private val parser = BillReminderParser()
    private val today = LocalDate(2026, 10, 2)

    private fun parse(body: String, app: String = "Globe") = parser.parse(app, body, today)

    @Test fun globeBillWithNamedBiller() {
        val bill = assertNotNull(parse("Your Globe Postpaid bill of PHP 1,499.00 is due on Oct 15. Pay now to avoid disconnection."))
        assertEquals("Globe Postpaid", bill.name)
        assertEquals(149_900L, bill.amountMinor)
        assertEquals(LocalDate(2026, 10, 15), bill.dueOn)
    }

    @Test fun meralcoUsesAppLabelWhenUnnamed() {
        val bill = assertNotNull(parse("Amount due: PHP 2,410.50. Due date: 2026-10-20.", app = "Meralco"))
        assertEquals("Meralco", bill.name)
        assertEquals(241_050L, bill.amountMinor)
        assertEquals(LocalDate(2026, 10, 20), bill.dueOn)
    }

    @Test fun cardStatementPrefersMinimumAmountDueAndDueDate() {
        val bill = assertNotNull(parse("Your statement dated Oct 1 is ready. Total PHP 20,000.00. Minimum amount due PHP 1,000.00 due 10/25/2026.", app = "BPI"))
        assertEquals(100_000L, bill.amountMinor)
        assertEquals(LocalDate(2026, 10, 25), bill.dueOn)
    }

    @Test fun totalAmountDueWinsOverMinimumAmountDue() {
        val bill = assertNotNull(parse("Minimum amount due PHP 500.00; total amount due PHP 2,000.00. Due Oct 15, 2026."))
        assertEquals(200_000L, bill.amountMinor)
    }

    @Test fun tagalogNotice() {
        val bill = assertNotNull(parse("Bayaran bago 15 Oct ang iyong Meralco bill na PHP 2,410.", app = "Meralco App"))
        assertEquals("Meralco", bill.name)
        assertEquals(241_000L, bill.amountMinor)
        assertEquals(LocalDate(2026, 10, 15), bill.dueOn)
    }

    @Test fun dateFormsAndYearInference() {
        assertEquals(LocalDate(2026, 10, 15), parse("Bill PHP 100 due October 15, 2026")?.dueOn)
        assertEquals(LocalDate(2026, 10, 15), parse("Bill PHP 100 due 15 October")?.dueOn)
        assertEquals(LocalDate(2027, 1, 5), parse("Bill PHP 100 due Jan 5")?.dueOn)
        assertEquals(LocalDate(2026, 9, 28), parse("Bill PHP 100 due Sep 28")?.dueOn)
        assertEquals(LocalDate(2027, 9, 24), parse("Bill PHP 100 due Sep 24")?.dueOn)
    }

    @Test fun promoWithoutAmountIsNotABill() {
        assertNull(parse("Statement ready! Pay on or before Oct 15 and enjoy 10% off your next order."))
    }

    @Test fun amountWithoutDateIsNotABill() {
        assertNull(parse("Your bill of PHP 2,410.00 is due soon."))
    }

    @Test fun missingCueIsNotABill() {
        assertNull(parse("PHP 2,410.00 sale ends Oct 15."))
    }

    @Test fun paymentReceivedStaysATransaction() {
        assertNull(parse("Payment of PHP 2,410 to MERALCO received", app = "GCash"))
        assertNull(parse("Your payment of PHP 2,410.00 for the bill due Oct 15 was successful.", app = "GCash"))
    }

    @Test fun impossibleDateIsRejected() {
        assertNull(parse("Amount due PHP 100.00 due Feb 30, 2027"))
    }

    @Test fun foreignCurrencyAmountIsRejected() {
        assertNull(parse("Amount due: USD 100. Due date: Oct 15"))
    }
}
