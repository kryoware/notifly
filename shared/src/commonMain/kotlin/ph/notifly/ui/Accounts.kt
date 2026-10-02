package ph.notifly.ui

import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.*

data class AccountBalance(val account: Account, val estimate: Long) {
    val manual get() = account.balanceAsOf.takeIf { it.toEpochMilliseconds() != Long.MIN_VALUE }
        ?.let { ManualBalance(account.balanceMinor, it) }
    val netValue get() = if (account.type == AccountType.CARD) -estimate else estimate
}

/** Keeps known IDs in their saved positions, then appends new accounts in repository order. */
internal fun homeAccountOrder(accounts: List<AccountBalance>, ids: List<Long>): List<AccountBalance> {
    val byId = accounts.associateBy { it.account.id }
    val saved = ids.distinct().mapNotNull(byId::get)
    val known = ids.toSet()
    return saved + accounts.filter { it.account.id !in known }
}

fun accountBalances(accounts: List<Account>, rows: List<Transaction>): List<AccountBalance> = accounts.map { account ->
    val movement = rows.filter { it.status == TransactionStatus.CONFIRMED && it.currency == "PHP" &&
        it.occurredAt > account.balanceAsOf }.sumOf { row ->
        when {
            row.type == TransactionType.TRANSFER && row.toAccountId == account.id -> row.amountMinor
            row.accountId != account.id -> 0L
            row.type == TransactionType.INCOME -> row.amountMinor
            else -> -row.amountMinor
        }
    }
    AccountBalance(account, account.balanceMinor + if (account.type == AccountType.CARD) -movement else movement)
}
