package ph.notifly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.*

class ImportAccountTest {
    private val transaction = Transaction(title = "Imported", amountMinor = 500,
        type = TransactionType.EXPENSE, category = "Other",
        status = TransactionStatus.NEEDS_REVIEW, sourceApp = null, captureId = null,
        accountId = 0, occurredAt = Instant.fromEpochMilliseconds(0))
    private val accounts = listOf(Account(1, "Wallet", AccountType.WALLET), Account(2, "Wallet", AccountType.BANK))

    @Test fun nameWithoutTypeRequiresExplicitAssignment() {
        val entry = TransactionCsv.Entry(transaction, accountName = "Wallet")
        assertNull(suggestedImportAccount(entry, false, accounts))
        assertNull(suggestedImportAccount(entry, false, accounts.take(1)))
    }

    @Test fun typedNamesOnlyMatchOneActiveAccountOfThatType() {
        val entry = TransactionCsv.Entry(transaction, accountName = "Wallet", accountType = AccountType.WALLET)
        assertEquals(1L, suggestedImportAccount(entry, false, accounts))
        assertNull(suggestedImportAccount(entry, false, accounts.map { it.copy(archived = true) }))
        assertNull(suggestedImportAccount(entry, false, accounts + Account(3, "Wallet", AccountType.WALLET)))
    }

    @Test fun linkedAppsStillRequireOneActiveMatch() {
        val entry = TransactionCsv.Entry(transaction.copy(sourceApp = "bank.app"))
        val linked = accounts.map { it.copy(linkedApps = setOf("bank.app")) }
        assertNull(suggestedImportAccount(entry, false, linked))
        assertEquals(1L, suggestedImportAccount(entry, false, linked.take(1)))
    }
}
