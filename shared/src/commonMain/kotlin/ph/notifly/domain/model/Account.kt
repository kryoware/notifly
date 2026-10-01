package ph.notifly.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Instant

enum class AccountType { BANK, CARD, WALLET }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val cardType: String? = null,
    val lastFour: String? = null,
    val freeTransfer: Boolean = false,
    val balanceMinor: Long = 0,
    val balanceAsOf: Instant = Instant.fromEpochMilliseconds(Long.MIN_VALUE),
    val dueDate: Int? = null,
    val statementDate: Int? = null,
    val archived: Boolean = false,
    val linkedApps: Set<String> = emptySet(),
) {
    fun validate() {
        require(name.isNotBlank()) { "Enter an account name." }
        require(lastFour == null || lastFour.matches(Regex("[0-9]{4}"))) { "Enter exactly four digits." }
        require(dueDate == null || dueDate in 1..31) { "Due day must be 1–31." }
        require(statementDate == null || statementDate in 1..31) { "Statement day must be 1–31." }
        require(type == AccountType.CARD || (cardType == null && dueDate == null && statementDate == null))
    }
}

data class Category(val id: Long = 0, val name: String, val type: TransactionType,
    val archived: Boolean = false, val budgetMinor: Long? = null)

val DEFAULT_EXPENSE_CATEGORIES = listOf("Food", "Transport", "Bills", "Shopping", "Other")
val DEFAULT_INCOME_CATEGORIES = listOf("Salary", "Business", "Interest", "Refunds", "Gifts", "Other")

data class CapturedDraft(
    val id: Long = 0, val captureId: Long? = null, val title: String, val amountMinor: Long,
    val type: TransactionType, val inbound: Boolean?, val occurredAt: Instant, val sourceApp: String,
    val accountId: Long? = null, val toAccountId: Long? = null,
    val accountHint: String? = null,
)

fun billingDate(year: Int, month: Int, day: Int): LocalDate {
    require(day in 1..31)
    val last = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    return LocalDate(year, month, minOf(day, last.day))
}

/** Only linked accounts are candidates. An explicit contradictory identifier prevents fallback. */
fun resolveAccount(accounts: List<Account>, sourceApp: String, body: String): Account? {
    val candidates = accounts.filter { !it.archived && sourceApp in it.linkedApps }
    return matchAccount(candidates, body, true)
}

private fun matchAccount(candidates: List<Account>, body: String, allowSingle: Boolean): Account? {
    val identifiers = Regex("(?i)(?:ending(?:\\s+in)?|ends(?:\\s+in)?|(?:account|acct|card)(?:\\s+(?:number|no\\.?))?)\\s*[:#*xX. -]*([0-9]{4,})\\b")
        .findAll(body).map { it.groupValues[1].takeLast(4) }.toSet()
    if (identifiers.isNotEmpty()) return candidates.filter { it.lastFour in identifiers }.singleOrNull()
    val named = candidates.filter { Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(it.name)}(?![\\p{L}\\p{N}])").containsMatchIn(body) }
    return named.singleOrNull() ?: candidates.singleOrNull().takeIf { named.isEmpty() && allowSingle }
}

/** For a transfer, separate the named counterparty from the notification's own account. */
fun resolveTransactionAccounts(accounts: List<Account>, sourceApp: String, body: String,
    type: TransactionType, inbound: Boolean?): Pair<Account?, Account?> {
    if (type != TransactionType.TRANSFER || inbound == null) return resolveAccount(accounts, sourceApp, body) to null
    val from = Regex("(?is)\\bfrom\\s+(.+?)(?=\\s+to\\s+|[;\\n]|$)").find(body)
    val to = Regex("(?is)\\bto\\s+(.+?)(?=\\s+from\\s+|[;\\n]|$)").find(body)
    val counterparty = if (inbound) from else to
    val ownSegment = (if (inbound) to else from)?.groupValues?.get(1)
        ?: counterparty?.let { body.removeRange(it.range) } ?: body
    val own = resolveAccount(accounts, sourceApp, ownSegment)
    val other = counterparty?.groupValues?.get(1)?.let { text ->
        matchAccount(accounts.filter { !it.archived && it.id != own?.id }, text, false)
    }
    return if (inbound) other to own else own to other
}
