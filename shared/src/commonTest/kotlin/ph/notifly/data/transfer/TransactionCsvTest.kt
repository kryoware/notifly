package ph.notifly.data.transfer

import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

class TransactionCsvTest {
    private val row = Transaction(id = 42, title = "=Merchant, \"name\"\nsecond line", amountMinor = 12345,
        type = TransactionType.TRANSFER, status = TransactionStatus.CONFIRMED, category = "Transfer",
        occurredAt = Instant.parse("2026-10-01T12:00:00Z"), sourceApp = "wallet", captureId = 99,
        note = "'a note\r\nwith commas, and quotes \"", fromApp = "wallet", toApp = "bank", accountId = 1)

    @Test fun roundTripPreservesMoneyAndTextButDropsDeviceIdsAndConfirmation() {
        val csv = TransactionCsv.encode(listOf(row))
        assertTrue(csv.contains("\"'=Merchant"))
        assertFalse(csv.contains("capture_id"))
        assertFalse(csv.contains("body"))
        val draft = TransactionCsv.decode("\uFEFF$csv").single()
        assertEquals(row.copy(id = 0, captureId = null, accountId = 0, status = TransactionStatus.NEEDS_REVIEW), draft)
        assertEquals(emptyList(), TransactionCsv.decode(TransactionCsv.encode(emptyList())))
        val simple = row.copy(title = "Merchant", note = "")
        assertEquals(simple.copy(id = 0, captureId = null, accountId = 0, status = TransactionStatus.NEEDS_REVIEW),
            TransactionCsv.decode(TransactionCsv.encode(listOf(simple)).replace("\n", "\r\n")).single())
        assertEquals(Long.MAX_VALUE, TransactionCsv.decode(TransactionCsv.encode(listOf(row.copy(amountMinor = Long.MAX_VALUE)))).single().amountMinor)
        val formula = row.copy(title = " \t=HYPERLINK(\"example\")")
        val protected = TransactionCsv.encode(listOf(formula))
        assertTrue(protected.contains("\"' \t=HYPERLINK"))
        assertEquals(formula.title, TransactionCsv.decode(protected).single().title)
    }

    @Test fun invalidFilesAreRejectedWithoutEchoingCellContents() {
        val csv = TransactionCsv.encode(listOf(row))
        for (bad in listOf(csv.replace("12345", "0"), csv.replace("12345", "12.34"),
            csv.replace("12345", "9223372036854775808"), csv.replace("PHP", "USD"),
            csv.replace("TRANSFER", "OTHER"), csv.replace("CONFIRMED", "OTHER"),
            csv.replace("2026-10-01T12:00:00Z", "not-a-date"), csv.dropLast(2),
            "wrong,header\n", csv.replace("\"12345\"", "\"12345\"extra"))) {
            val error = assertFailsWith<IllegalArgumentException> { TransactionCsv.decode(bad) }
            assertFalse(error.message.orEmpty().contains("Merchant"))
        }
        assertFailsWith<IllegalArgumentException> { TransactionCsv.decode("x".repeat(TransactionCsv.MAX_CHARS + 1)) }
        assertFailsWith<IllegalArgumentException> { TransactionCsv.decode(TransactionCsv.encode(List(TransactionCsv.MAX_ROWS + 1) { row })) }
    }
}
