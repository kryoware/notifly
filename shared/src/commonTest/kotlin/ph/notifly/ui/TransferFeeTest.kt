package ph.notifly.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TransferFeeTest {
    @Test fun feeIsExplicitNonNegativeCentavos() {
        assertEquals(0L, parseFeeMinor("0.00"))
        assertEquals(1525L, parseFeeMinor("15.25"))
        listOf("", "-1", "0.001", "1.234", "92233720368547758.08").forEach { assertNull(parseFeeMinor(it)) }
        assertEquals("Bank · Visa · Ending 1234", Account(1, "Bank", AccountType.CARD,
            cardType = "Visa", lastFour = "1234").pickerLabel())
    }

    @Test fun feeOnlyLeavesOriginAndCountsOnlyAfterConfirmation() = runTest {
        val now = Instant.parse("2026-10-03T12:00:00Z")
        val row = Transaction(title = "Move savings", amountMinor = 10000, type = TransactionType.TRANSFER,
            status = TransactionStatus.NEEDS_REVIEW, category = "Transfer", occurredAt = now,
            accountId = 1, toAccountId = 2, sourceApp = null, captureId = null, feeMinor = 1500)
        val accounts = listOf(Account(1, "Bank", AccountType.BANK, balanceMinor = 50000),
            Account(2, "Card", AccountType.CARD, balanceMinor = 20000))
        assertEquals(listOf(50000L, 20000L), accountBalances(accounts, listOf(row)).map { it.estimate })
        val confirmed = row.copy(status = TransactionStatus.CONFIRMED)
        assertEquals(listOf(38500L, 10000L), accountBalances(accounts, listOf(confirmed)).map { it.estimate })
        val today = now.toLocalDateTime(TimeZone.UTC).date
        assertEquals(0L, monthInsights(listOf(row), today, TimeZone.UTC).flow.spent)
        assertEquals(1500L, monthInsights(listOf(confirmed), today, TimeZone.UTC).flow.spent)
        assertEquals(listOf(CategorySpend("Transfer fees", 1500, 0)),
            windowInsights(listOf(confirmed), today, 7, TimeZone.UTC).categories)
        assertEquals("Transfer fee · Move savings", windowInsights(listOf(confirmed), today, 7, TimeZone.UTC).largest.single().title)
        assertEquals(1500L, TransactionCsv.decode(TransactionCsv.encode(listOf(row))).single().feeMinor)
        assertEquals(1500L, confirmed.toSyncTransaction().feeMinor)
        val repository = DemoTransactions()
        val before = repository.observeConfirmedNetMinor().first()
        val id = repository.upsert(row)
        assertEquals(before, repository.observeConfirmedNetMinor().first())
        repository.upsert(confirmed.copy(id = id))
        assertEquals(before - 1500, repository.observeConfirmedNetMinor().first())
    }

    @Test fun editorRequiresFeePersistsItAndFreeAccountsSkipIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = DemoTransactions()
        val ledger = DemoLedger(repository)
        val editor = EditorModel(repository, 0, ledger = ledger, apps = DemoAllowList())
        try {
            runCurrent()
            editor.edit(title = "Transfer test", amount = "100", type = TransactionType.TRANSFER,
                accountId = 1, toAccountId = 2)
            editor.save()
            assertNotNull(editor.state.value.feeError)
            assertTrue(repository.observeAll().first().none { it.title == "Transfer test" })
            editor.edit(fee = "15")
            editor.save()
            runCurrent()
            val saved = repository.observeAll().first().first { it.title == "Transfer test" }
            assertEquals(1500L, saved.feeMinor)
            ledger.saveAccount(ledger.observeAccounts().first().first { it.id == 1L }.copy(freeTransfer = true))
            runCurrent()
            editor.edit(title = "Free transfer", fee = "")
            editor.save()
            runCurrent()
            assertEquals(0L, repository.observeAll().first().first { it.title == "Free transfer" }.feeMinor)
            val existing = EditorModel(repository, saved.id, ledger = ledger, apps = DemoAllowList())
            runCurrent()
            assertEquals("15.00", existing.state.value.fee)
            assertTrue(existing.state.value.feeRequired)
            existing.edit(title = "Edited transfer")
            existing.save()
            runCurrent()
            assertEquals(1500L, repository.byId(saved.id)?.feeMinor)
            existing.viewModelScope.cancel()
        } finally {
            editor.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
