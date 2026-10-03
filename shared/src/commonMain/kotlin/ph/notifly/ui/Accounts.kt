package ph.notifly.ui

import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.*

data class AccountBalance(val account: Account, val estimate: Long) {
    val manual get() = account.balanceAsOf.takeIf { it.toEpochMilliseconds() != Long.MIN_VALUE }
        ?.let { ManualBalance(account.balanceMinor, it) }
    val netValue get() = if (account.type == AccountType.CARD) -estimate else estimate
}

fun accountBalances(accounts: List<Account>, rows: List<Transaction>): List<AccountBalance> = accounts.map { account ->
    val movement = rows.filter { it.status == TransactionStatus.CONFIRMED && it.currency == "PHP" &&
        it.occurredAt > account.balanceAsOf }.sumOf { row ->
        when {
            row.type == TransactionType.TRANSFER && row.toAccountId == account.id -> row.amountMinor
            row.accountId != account.id -> 0L
            row.type == TransactionType.INCOME -> row.amountMinor
            row.type == TransactionType.TRANSFER -> -row.amountMinor - row.feeMinor
            else -> -row.amountMinor
        }
    }
    AccountBalance(account, account.balanceMinor + if (account.type == AccountType.CARD) -movement else movement)
}

internal fun Account.pickerLabel(): String = listOfNotNull(name, cardType?.takeIf { it.isNotBlank() },
    lastFour?.let { "Ending $it" }).joinToString(" · ")
