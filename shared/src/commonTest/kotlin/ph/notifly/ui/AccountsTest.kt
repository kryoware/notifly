package ph.notifly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

class AccountsTest {
    private fun row(app: String?, amountMinor: Long, type: TransactionType, at: Long,
                    status: TransactionStatus = TransactionStatus.CONFIRMED) =
        Transaction(title = "[TEST] row", amountMinor = amountMinor, type = type, status = status, category = "Other",
            occurredAt = Instant.fromEpochMilliseconds(at), sourceApp = app, captureId = null)

    private val apps = listOf(
        AllowedApp("com.maya", "Maya", "Wallet", listening = true, finance = true),
        AllowedApp("com.gcash", "GCash", "Wallet", listening = true, finance = true),
        AllowedApp("com.shop", "Shop", "Shopping", listening = true),
    )
    private val rows = listOf(
        row("com.maya", 10_000, TransactionType.INCOME, 100),
        row("com.maya", 2_500, TransactionType.EXPENSE, 300),
        row("com.maya", 99_999, TransactionType.EXPENSE, 400, TransactionStatus.NEEDS_REVIEW),
        row("com.maya", 50_000, TransactionType.TRANSFER, 500),
        row("com.gcash", 1_000, TransactionType.EXPENSE, 100),
        row(null, 7_000, TransactionType.INCOME, 100),
    )

    @Test fun estimatesFromConfirmedCapturesOfFinanceAppsOnly() {
        val balances = accountBalances(apps, rows, emptyMap())
        assertEquals(listOf("com.maya" to 7_500L, "com.gcash" to -1_000L), balances.map { it.app.packageName to it.estimate })
    }

    @Test fun manualBalanceCountsOnlyLaterTransactions() {
        val manual = ManualBalance(100_000, Instant.fromEpochMilliseconds(200))
        val maya = accountBalances(apps, rows, mapOf("com.maya" to manual)).first()
        assertEquals(97_500L, maya.estimate)
        assertEquals(manual, maya.manual)
    }

    @Test fun confirmedTransferMovesBothEnds() {
        val transfer = row("com.maya", 3_000, TransactionType.TRANSFER, 600).copy(fromApp = "com.maya", toApp = "com.gcash")
        val balances = accountBalances(apps, rows + transfer, emptyMap())
        assertEquals(listOf("com.maya" to 4_500L, "com.gcash" to 2_000L), balances.map { it.app.packageName to it.estimate })
    }
}
