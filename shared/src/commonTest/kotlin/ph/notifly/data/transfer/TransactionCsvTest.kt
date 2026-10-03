package ph.notifly.data.transfer

import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

class TransactionCsvTest {
    @Test fun feesRoundTripAndOlderFilesDefaultToZero() {
        val simple = row.copy(title = "Transfer", note = "", feeMinor = 1500)
        val csv = TransactionCsv.encode(listOf(simple))
        assertEquals(1500L, TransactionCsv.decode(csv).single().feeMinor)
        val v2 = csv.trimEnd().lines().joinToString("\n") { it.substringBeforeLast(',') }
        assertEquals(0L, TransactionCsv.decode(v2).single().feeMinor)
        val v1 = v2.lines().joinToString("\n") { line -> line.split(',').take(12).joinToString(",") }
        assertEquals(0L, TransactionCsv.decode(v1).single().feeMinor)
        assertFailsWith<IllegalArgumentException> { TransactionCsv.decode(csv.replace("\"1500\"", "\"-1\"")) }
    }
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

    private val budge = """
        Format version,Period,User ID
        1,01-01-2026..31-01-2026,user

        ###

        Date,Payment,Is paid,Amount,Currency,Account,Category,Subcategory,Goal,Description
        05-01-2026,Coffee Shop ,true,-590.80,PHP,Card A,Dining,Delivery,,late
        05-01-2026,Coffee Shop ,true,-590.80,PHP,Card A,Dining,Delivery,,late
        06-01-2026,,true,1500.5,PHP,Bank B,,,,
        07-01-2026,Planned,false,-100,PHP,Bank B,Bills,,,

        ###

        Account,Account Balance,Available Balance,Credit Limit,Currency,Is savings,Description
        Card A,-100,900,1000,PHP,false,

        ###

        Goal,whatever,mangled
        Rainy day,1,2
    """.trimIndent()

    @Test fun budgeExportImportsPaidRowsAsReviewDraftsAndIgnoresGoals() {
        val zone = kotlinx.datetime.TimeZone.of("Asia/Manila")
        val rows = TransactionCsv.decodeEntries(budge, zone)
        assertEquals(3, rows.size)
        val coffee = rows[0].transaction
        assertEquals(59080, coffee.amountMinor)
        assertEquals(TransactionType.EXPENSE, coffee.type)
        assertEquals(TransactionStatus.NEEDS_REVIEW, coffee.status)
        assertEquals("Coffee Shop", coffee.title)
        assertEquals("Dining", coffee.category)
        assertEquals("Delivery · late", coffee.note)
        assertEquals(Instant.parse("2026-01-04T16:00:00Z"), coffee.occurredAt)
        assertEquals("Card A", rows[0].accountName)
        assertNull(rows[0].accountType)
        assertNotEquals(coffee.createdAt, rows[1].transaction.createdAt)
        val income = rows[2].transaction
        assertEquals(150050, income.amountMinor)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("Other", income.title)
        assertEquals(rows, TransactionCsv.decodeEntries(budge, zone))
    }

    @Test fun budgeExportsWithUnexpectedShapeAreRejected() {
        for (bad in listOf(budge.replace("1,01-01", "2,01-01"), budge.replace("Is paid", "Paid"),
            budge.replace("Credit Limit", "Limit"), budge.replace("Goal,whatever", "Other,whatever"),
            budge.replace("-590.80", "-590.801"), budge.replace("-590.80", "0"), budge.replace("-590.80", "abc"),
            budge.replace("05-01-2026", "31-02-2026"), budge.replace("PHP,Card A", "USD,Card A"),
            budge.replace(",true,", ",yes,"), budge.substringBefore("Date,Payment"),
            budge.substringBefore("Account,Account Balance"))) {
            val error = assertFailsWith<IllegalArgumentException> { TransactionCsv.decodeEntries(bad) }
            assertFalse(error.message.orEmpty().contains("Coffee"))
        }
    }

    @Test fun budgeRowsThatDifferOnlyInIgnoredOrTrimmedFieldsAreAllKept() {
        val csv = budge.replace("06-01-2026,,true,1500.5,PHP,Bank B,,,,",
            "05-01-2026,Coffee Shop,true,-590.80,PHP,Card A ,Dining,Delivery,Goal X,late")
        val times = TransactionCsv.decodeEntries(csv).map { it.transaction.createdAt }
        assertEquals(3, times.toSet().size)
    }

    @Test fun budgeLimitCountsOnlyTransactionRows() {
        fun file(count: Int) = budge.substringBefore("05-01-2026") +
            List(count) { "05-01-2026,Shop,true,-1,PHP,Card A,Dining,,,\n" }.joinToString("") +
            "\n###\n\n" + budge.substringAfter("###\n\n").substringAfter("###\n\n")
        assertEquals(TransactionCsv.MAX_ROWS, TransactionCsv.decodeEntries(file(TransactionCsv.MAX_ROWS)).size)
        assertFailsWith<IllegalArgumentException> { TransactionCsv.decodeEntries(file(TransactionCsv.MAX_ROWS + 1)) }
    }
}
