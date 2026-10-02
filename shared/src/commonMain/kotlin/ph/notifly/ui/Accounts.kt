package ph.notifly.ui

import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.*

data class AccountBalance(val account: Account, val estimate: Long) {
    val manual get() = account.balanceAsOf.takeIf { it.toEpochMilliseconds() != Long.MIN_VALUE }
        ?.let { ManualBalance(account.balanceMinor, it) }
    val netValue get() = if (account.type == AccountType.CARD) -estimate else estimate
}

/** An account looks like its first linked app; accounts without one have no entry. */
fun accountIcons(accounts: List<Account>): Map<Long, String> =
    accounts.mapNotNull { a -> a.linkedApps.minOrNull()?.let { a.id to it } }.toMap()

fun accountBalances(accounts: List<Account>, rows: List<Transaction>): List<AccountBalance> = accounts.map { account ->
    val movement = rows.filter { it.status == TransactionStatus.CONFIRMED && it.currency == "PHP" &&
        it.occurredAt > account.balanceAsOf }.sumOf { row ->
        when {
            row.type == TransactionType.TRANSFER && row.toAccountId == account.id -> row.amountMinor
            row.accountId != account.id -> 0L
            row.type == TransactionType.INCOME -> row.amountMinor
            else -> -row.amountMinor - row.feeMinor
        }
    }
    AccountBalance(account, account.balanceMinor + if (account.type == AccountType.CARD) -movement else movement)
}
