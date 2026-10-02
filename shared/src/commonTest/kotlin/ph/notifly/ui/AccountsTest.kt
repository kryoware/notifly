package ph.notifly.ui

import kotlin.test.*
import kotlin.time.Instant
import ph.notifly.domain.model.*

class AccountsTest {
    @Test fun homeOrderReconcilesWithoutChangingBalancesOrVisibility() {
        val accounts = listOf(
            AccountBalance(Account(1, "Alpha", AccountType.BANK), -50L),
            AccountBalance(Account(2, "Beta", AccountType.WALLET, archived = true), 100L),
            AccountBalance(Account(3, "New account", AccountType.CARD), 500L))
        assertEquals(accounts, homeAccountOrder(accounts, emptyList()))
        assertEquals(listOf(2L, 1L, 3L), homeAccountOrder(accounts, listOf(9L, 2L, 2L, 1L)).map { it.account.id })
        assertEquals(listOf(accounts[1], accounts[2]), homeAccountOrder(accounts.drop(1), listOf(2L, 1L)))
        assertEquals(emptyList(), homeAccountOrder(emptyList(), listOf(2L, 1L)))
        assertEquals(accounts.take(1), homeAccountOrder(accounts.take(1), listOf(2L, 1L)))
    }
    private val at = Instant.fromEpochMilliseconds(1000)
    private fun row(account: Long, amount: Long, type: TransactionType, status: TransactionStatus = TransactionStatus.CONFIRMED,
        to: Long? = null, time: Long = 2000) = Transaction(title = "Test", amountMinor = amount,
        type = type, status = status, category = "Other", occurredAt = Instant.fromEpochMilliseconds(time),
        sourceApp = null, captureId = null, accountId = account, toAccountId = to)
    private val bank = Account(1, "Bank", AccountType.BANK, balanceMinor = 10000, balanceAsOf = at)
    private val card = Account(2, "Card", AccountType.CARD, balanceMinor = 5000, balanceAsOf = at)

    @Test fun debtPurchasesRefundsPaymentsAndPending() {
        val rows = listOf(row(2, 1000, TransactionType.EXPENSE), row(2, 200, TransactionType.INCOME),
            row(1, 2000, TransactionType.TRANSFER, to = 2), row(2, 99999, TransactionType.EXPENSE, TransactionStatus.NEEDS_REVIEW))
        val balances = accountBalances(listOf(bank, card), rows)
        assertEquals(listOf(8000L, 3800L), balances.map { it.estimate })
        assertEquals(4200L, balances.sumOf { it.netValue })
        assertEquals(5000L, accountBalances(listOf(bank, card), emptyList()).sumOf { it.netValue })
    }
    @Test fun reconciliationExcludesEarlierAndEqualTimesAndAllowsNegativeCredit() {
        val rows = listOf(row(1, 9999, TransactionType.EXPENSE, time = 999), row(1, 9999, TransactionType.EXPENSE, time = 1000),
            row(1, 300, TransactionType.INCOME), row(2, 6000, TransactionType.INCOME))
        val balances = accountBalances(listOf(bank, card), rows)
        assertEquals(10300L, balances[0].estimate)
        assertEquals(-1000L, balances[1].estimate)
    }
    @Test fun deletionAndEditingRecomputeInsteadOfAccumulatingDrift() {
        val expense = row(1, 1000, TransactionType.EXPENSE)
        assertEquals(9000L, accountBalances(listOf(bank), listOf(expense)).single().estimate)
        assertEquals(9500L, accountBalances(listOf(bank), listOf(expense.copy(amountMinor = 500))).single().estimate)
        assertEquals(10000L, accountBalances(listOf(bank), emptyList()).single().estimate)
    }
    @Test fun accountMatchingNeverGuessesAcrossIdentifiers() {
        val a = bank.copy(lastFour = "1234", linkedApps = setOf("bank"))
        val b = card.copy(lastFour = "5678", linkedApps = setOf("bank"))
        assertEquals(a, resolveAccount(listOf(a, b), "bank", "Account ending 1234 debited PHP 10"))
        assertNull(resolveAccount(listOf(a, b), "bank", "You paid PHP 10"))
        assertNull(resolveAccount(listOf(a), "bank", "Card ending 5678 charged PHP 10"))
        assertNull(resolveAccount(listOf(a.copy(archived = true)), "bank", "You paid PHP 10"))
        assertEquals(a, resolveAccount(listOf(a), "bank", "You paid PHP 10"))
        assertNull(resolveAccount(listOf(a), "other", "Account ending 1234 debited PHP 10"))
    }
    @Test fun transferSeparatesSourceAndDestinationIdentifiers() {
        val a = bank.copy(lastFour = "1234", linkedApps = setOf("bank"))
        val b = card.copy(lastFour = "5678", linkedApps = setOf("bank"))
        assertEquals(a to b, resolveTransactionAccounts(listOf(a, b), "bank",
            "Transferred PHP 10 from your account ending 1234 to your card ending 5678", TransactionType.TRANSFER, false))
        assertEquals(a to b, resolveTransactionAccounts(listOf(a, b), "bank",
            "Received PHP 10 from your account ending 1234 to your card ending 5678", TransactionType.TRANSFER, true))
        val wallet = bank.copy(name = "Wallet", linkedApps = setOf("wallet"), lastFour = null)
        assertEquals(wallet to b, resolveTransactionAccounts(listOf(wallet, b), "wallet",
            "Sent PHP 10 to your card ending 5678", TransactionType.TRANSFER, false))
    }

    @Test fun billingDaysAndBalanceInputs() {
        assertEquals("2026-02-28", billingDate(2026, 2, 31).toString())
        assertEquals("2028-02-29", billingDate(2028, 2, 31).toString())
        assertEquals(-1234L, balanceInput("-12.34"))
        assertEquals(0L, balanceInput("0.00"))
        assertNull(balanceInput("1.001"))
    }
}
