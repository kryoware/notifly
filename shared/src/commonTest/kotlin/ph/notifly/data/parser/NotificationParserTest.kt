package ph.notifly.data.parser

import ph.notifly.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * These strings mirror the seeded notification log in the prototype.
 * Add a real case here every time the log shows something parsed wrong —
 * this file is the regression net for the parser.
 */
class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun `gcash inbound payroll is income`() {
        val r = parser.parse("You received PHP 48,000.00 from ACME CORP. Ref 8812 4410.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(4_800_000L, p.draft.amountMinor)
        assertEquals(TransactionType.INCOME, p.draft.type)
        assertEquals("ACME CORP", p.draft.merchant)
    }

    @Test
    fun `maya outbound payment is expense`() {
        val r = parser.parse("Payment of PHP 189.00 to GRAB PH.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(18_900L, p.draft.amountMinor)
        assertEquals(TransactionType.EXPENSE, p.draft.type)
    }

    @Test
    fun `bpi debit ignores account digits as the amount`() {
        val r = parser.parse(
            "Your BPI account ending 4417 has been debited PHP 2,340.00 for MERALCO BILL PAYMENT.",
        )
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(234_000L, p.draft.amountMinor)
        assertEquals(TransactionType.EXPENSE, p.draft.type)
    }

    @Test
    fun `balance notice is not a transaction`() {
        val r = parser.parse("Your GCash balance is PHP 12,904.55 as of 3:00 PM.")
        val u = assertIs<ParseOutcome.Unrecognized>(r)
        assertTrue(u.reason.contains("balance notice"))
    }

    @Test
    fun `notification without an amount is unrecognized`() {
        val r = parser.parse("Paalala: I-verify ang iyong account bago mag-expire sa Dis 31.")
        assertIs<ParseOutcome.Unrecognized>(r)
    }

    @Test
    fun `self transfer is flagged rather than counted as spending`() {
        val r = parser.parse("You sent PHP 1,000.00 to your BPI Savings ending 9921.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(TransactionType.TRANSFER, p.draft.type)
        assertTrue(p.draft.needsReview)
    }

    @Test
    fun `preauth hold is low confidence on amount`() {
        val r = parser.parse("PHP 500.00 hold placed by SHELL STATION 12. Final amount may differ.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(Confidence.LOW, p.draft.amountConfidence)
        assertTrue(p.draft.needsReview)
    }

    @Test
    fun `peso sign and no decimals still parse`() {
        val r = parser.parse("₱158 paid to 7-ELEVEN BAJADA.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(15_800L, p.draft.amountMinor)
    }

    @Test
    fun `minor unit conversion is exact`() {
        assertEquals(4_800_000L, parser.toMinorUnits("48,000.00"))
        assertEquals(10L, parser.toMinorUnits("0.10"))
        assertEquals(100L, parser.toMinorUnits("1"))
    }

    @Test
    fun `thousands separator without decimals still parses`() {
        val r = parser.parse("Paid PHP 1,200 to LANDBANK.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(120_000L, p.draft.amountMinor)
        assertEquals(TransactionType.EXPENSE, p.draft.type)
    }

    @Test
    fun `two amounts picks the transaction not the trailing balance`() {
        val r = parser.parse("You paid PHP 250.00 to JOLLIBEE. Available balance: PHP 15,000.00.")
        val p = assertIs<ParseOutcome.Parsed>(r)
        assertEquals(25_000L, p.draft.amountMinor)
        assertEquals(TransactionType.EXPENSE, p.draft.type)
    }
}
