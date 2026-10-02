package ph.notifly.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val GCASH = "com.globe.gcash.android"
private const val MAYA = "com.paymaya"

private class DemoExpense(val title: String, val category: String, val app: String, val lowPesos: Int, val highPesos: Int)

private val DEMO_EXPENSES = listOf(
    DemoExpense("SM Supermarket", "Food", GCASH, 800, 3200), DemoExpense("Jollibee", "Food", MAYA, 150, 650),
    DemoExpense("Grab", "Transport", GCASH, 120, 480), DemoExpense("Shell", "Transport", MAYA, 1000, 2500),
    DemoExpense("Meralco", "Bills", GCASH, 2200, 4800), DemoExpense("Globe Load", "Bills", GCASH, 99, 599),
    DemoExpense("Lazada", "Shopping", MAYA, 300, 2800), DemoExpense("Starbucks", "Food", GCASH, 180, 380),
)

/** A fresh, randomised month: salary, everyday spending, a transfer, and a few items still awaiting review. */
fun demoRows(random: Random = Random.Default): List<Transaction> {
    val now = Clock.System.now()
    fun ago(maxDays: Int) = now - random.nextInt(0, maxDays * 24 * 60).minutes
    fun pesos(low: Int, high: Int) = random.nextLong(low.toLong(), high.toLong() + 1) * 100 + random.nextLong(0, 100)
    fun expense(status: TransactionStatus, maxDays: Int) = DEMO_EXPENSES.random(random).let {
        Transaction(title = it.title, amountMinor = pesos(it.lowPesos, it.highPesos), type = TransactionType.EXPENSE,
            status = status, category = it.category, occurredAt = ago(maxDays), sourceApp = it.app, captureId = null, accountId = if (it.app == GCASH) 1 else 2)
    }
    val expenses = List(14) { expense(TransactionStatus.CONFIRMED, 28) }
    val pending = List(3) { expense(TransactionStatus.NEEDS_REVIEW, 2) }
    val salary = Transaction(title = "ACME Corp payroll", amountMinor = pesos(28000, 42000), type = TransactionType.INCOME,
        status = TransactionStatus.CONFIRMED, category = "Income", occurredAt = now - random.nextInt(10, 15).days,
        sourceApp = GCASH, captureId = null, accountId = 1)
    val cashIns = listOf(GCASH to "GCash cash in", MAYA to "Maya cash in").map { (app, title) ->
        Transaction(title = title, amountMinor = pesos(6000, 12000), type = TransactionType.INCOME, status = TransactionStatus.CONFIRMED,
            category = "Income", occurredAt = ago(26), sourceApp = app, captureId = null, accountId = if (app == GCASH) 1 else 2)
    }
    val freelance = Transaction(title = "Freelance payment", amountMinor = pesos(3500, 9000), type = TransactionType.INCOME,
        status = TransactionStatus.CONFIRMED, category = "Income", occurredAt = ago(6), sourceApp = GCASH, captureId = null, accountId = 1)
    val refund = Transaction(title = "Lazada refund", amountMinor = pesos(200, 900), type = TransactionType.INCOME,
        status = TransactionStatus.NEEDS_REVIEW, category = "Income", occurredAt = ago(1), sourceApp = MAYA, captureId = null, accountId = 1)
    val transfer = Transaction(title = "GCash to Maya", amountMinor = pesos(2000, 5000), type = TransactionType.TRANSFER,
        status = TransactionStatus.CONFIRMED, category = "Transfer", occurredAt = ago(20), sourceApp = GCASH, captureId = null,
        fromApp = GCASH, toApp = MAYA, toAccountId = 2, accountId = 1)
    return (expenses + pending + cashIns + salary + freelance + refund + transfer)
        .sortedByDescending { it.occurredAt }
        .mapIndexed { index, row -> row.copy(id = index + 1L) }
}

/** Isolated, explicitly selected demo data; never inserted into the user's ledger. */
class DemoTransactions(initial: List<Transaction> = listOf(
    Transaction(1, "ACME CORP", 4_800_000_000, type = TransactionType.INCOME, status = TransactionStatus.CONFIRMED,
        category = "Income", occurredAt = Clock.System.now(), sourceApp = "GCash", captureId = null, accountId = 1),
    Transaction(2, "SM Supermarket", 245050, type = TransactionType.EXPENSE, status = TransactionStatus.NEEDS_REVIEW,
        category = "Shopping", occurredAt = Clock.System.now(), sourceApp = "GCash", captureId = null, accountId = 1),
)) : TransactionRepository {
    private val rows = MutableStateFlow(initial)
    private var nextId = rows.value.size + 1L
    override fun observeAll() = rows
    override fun observeByStatus(status: TransactionStatus) = rows.map { it.filter { t -> t.status == status } }
    override fun observeConfirmedSince(since: Instant, limit: Int) = rows.map { values ->
        values.filter { it.status == TransactionStatus.CONFIRMED && it.occurredAt >= since }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.createdAt })
            .take(limit)
    }
    override suspend fun byId(id: Long) = rows.value.find { it.id == id }
    override suspend fun importTransactions(transactions: List<Transaction>): Int {
        val known = rows.value.map { it.copy(id = 0, captureId = null, status = TransactionStatus.NEEDS_REVIEW) }.toMutableSet()
        val added = transactions.map { it.copy(id = 0, captureId = null, status = TransactionStatus.NEEDS_REVIEW) }.filter { known.add(it) }
        added.forEach { upsert(it) }
        return added.size
    }
    override suspend fun upsert(transaction: Transaction): Long {
        require(transaction.accountId > 0 && transaction.amountMinor > 0 && transaction.title.isNotBlank() && transaction.currency == "PHP")
        require(transaction.type != TransactionType.TRANSFER || (transaction.toAccountId != null && transaction.toAccountId != transaction.accountId))
        val id = transaction.id.takeIf { it != 0L } ?: nextId++
        rows.value = (rows.value.filterNot { it.id == id } + transaction.copy(id = id))
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.createdAt })
        return id
    }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override fun observeConfirmedNetMinor() = rows.map { list ->
        list.filter { it.status == TransactionStatus.CONFIRMED }.sumOf {
            when (it.type) { TransactionType.INCOME -> it.amountMinor; TransactionType.EXPENSE -> -it.amountMinor; TransactionType.TRANSFER -> 0L }
        }
    }
}

class DemoAllowList : AllowListRepository {
    private val rows = MutableStateFlow(listOf(
        AllowedApp(GCASH, "GCash", "Wallet", true, finance = true),
        AllowedApp(MAYA, "Maya", "Wallet", true, finance = true),
        AllowedApp("com.bpi.ng.app", "BPI Mobile", "Bank", false),
    ))
    override fun observeAll() = rows
    override suspend fun isAllowed(packageName: String) = rows.value.any { it.packageName == packageName && it.listening }
    override suspend fun setListening(packageName: String, listening: Boolean) {
        rows.value = rows.value.map { if (it.packageName == packageName) it.copy(listening = listening) else it }
    }
    override suspend fun setFinance(packageName: String, finance: Boolean) {
        rows.value = rows.value.map { if (it.packageName == packageName) it.copy(finance = finance) else it }
    }
    override suspend fun incrementCapturedCount(packageName: String) {
        rows.value = rows.value.map { if (it.packageName == packageName) it.copy(capturedCount = it.capturedCount + 1) else it }
    }
}

class DemoCaptures : CaptureRepository {
    private val transactions = DemoTransactions()
    override suspend fun recordDraft(capture: RawCapture, draft: CapturedDraft): Long = record(capture)
    override suspend fun recordParsed(capture: RawCapture, transaction: Transaction): Long {
        val id = record(capture)
        transactions.upsert(transaction.copy(captureId = id))
        return id
    }
    private val rows = MutableStateFlow(listOf(
        RawCapture(1, "GCash", Clock.System.now(), "You received PHP 480.00 from ACME CORP.", CaptureResult.PARSED, "PHP 480.00", "received", "Matched an amount and an income keyword. Still requires your confirmation."),
        RawCapture(2, "Maya", Clock.System.now(), "PHP 500.00 hold placed by SHELL.", CaptureResult.NEEDS_REVIEW, "PHP 500.00", "hold", "Possible pre-authorisation. Check the final amount before confirming."),
        RawCapture(3, "GCash", Clock.System.now(), "Your balance is PHP 9120.40.", CaptureResult.UNRECOGNIZED, reason = "Balance notice without a transaction verb. No transaction created."),
    ))
    override fun observeLog(filter: CaptureResult?) = rows.map { list ->
        list.filter { filter == null || it.result == filter }.sortedByDescending { it.capturedAt }
    }
    override suspend fun record(capture: RawCapture): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.value = rows.value + capture.copy(id = id)
        return id
    }
    override suspend fun clearLog() { rows.value = emptyList() }
    override suspend fun redactBodies() { rows.value = rows.value.map { it.copy(body = null) } }
    override suspend fun purgeExpired() { rows.value = rows.value.filter { it.capturedAt >= Clock.System.now() - kotlin.time.Duration.parse("24h") } }
}

class DemoLedger(private val transactions: TransactionRepository) : LedgerRepository {
    private val accounts = MutableStateFlow(listOf(
        Account(1, "GCash wallet", AccountType.WALLET, linkedApps = setOf(GCASH)),
        Account(2, "Maya wallet", AccountType.WALLET, linkedApps = setOf(MAYA))))
    private val categories = MutableStateFlow((DEFAULT_EXPENSE_CATEGORIES.map { it to TransactionType.EXPENSE } +
        (DEFAULT_INCOME_CATEGORIES + "Income").map { it to TransactionType.INCOME }).mapIndexed { i, (name, type) -> Category(i + 1L, name, type) })
    private val drafts = MutableStateFlow(listOf(CapturedDraft(1, title = "Purchase awaiting account assignment",
        amountMinor = 12500, type = TransactionType.EXPENSE, inbound = false, occurredAt = Clock.System.now(), sourceApp = MAYA)))
    override suspend fun initialize() = Unit
    override fun observeAccounts() = accounts
    override fun observeCategories() = categories
    override fun observeDrafts() = drafts
    override suspend fun saveAccount(account: Account): Long {
        account.validate()
        val old = accounts.value.find { it.id == account.id }
        require(old == null || (old.type == AccountType.CARD) == (account.type == AccountType.CARD) ||
            transactions.observeAll().first().none { it.accountId == account.id || it.toAccountId == account.id })
        require(accounts.value.none { it.id != account.id && it.name.equals(account.name, true) })
        val id = account.id.takeIf { it > 0 } ?: (accounts.value.maxOfOrNull { it.id } ?: 0) + 1
        accounts.value = accounts.value.filterNot { it.id == id } + account.copy(id = id)
        return id
    }
    override suspend fun deleteAccount(id: Long) {
        require(transactions.observeAll().first().none { it.accountId == id || it.toAccountId == id })
        accounts.value = accounts.value.filterNot { it.id == id }
    }
    override suspend fun saveCategory(category: Category): Long {
        require(category.name.isNotBlank() && category.type != TransactionType.TRANSFER)
        val old = categories.value.find { it.id == category.id }
        require(old?.name != "Other" || (category.name == "Other" && !category.archived))
        val id = category.id.takeIf { it > 0 } ?: (categories.value.maxOfOrNull { it.id } ?: 0) + 1
        categories.value = categories.value.filterNot { it.id == id } + category.copy(id = id)
        transactions.observeAll().first().filter { it.categoryId == id || (it.categoryId == null && it.category == old?.name && it.type == category.type) }
            .forEach { transactions.upsert(it.copy(categoryId = id, category = category.name)) }
        return id
    }
    override suspend fun deleteDraft(id: Long) { drafts.value = drafts.value.filterNot { it.id == id } }
    override suspend fun confirmDraft(id: Long, transaction: Transaction): Long {
        require(drafts.value.any { it.id == id })
        val saved = transactions.upsert(transaction); deleteDraft(id); return saved
    }
}

/**
 * Isolated demo bills, relative to today so every bucket has something in it: upcoming, overdue, paid,
 * and one detected bill awaiting review. [rows] supplies the transaction the paid bill is linked to.
 */
class DemoBills(rows: List<Transaction> = emptyList()) : BillRepository {
    private val now = Clock.System.now()
    private val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    private fun bill(id: Long, name: String, pesos: Long, inDays: Int, repeat: BillRepeat, accountId: Long = 1,
                     status: TransactionStatus = TransactionStatus.CONFIRMED, detected: Boolean = false, settled: Int = 0) =
        Bill(id, name, pesos * 100, accountId = accountId, startsOn = today.plus(inDays, DateTimeUnit.DAY), repeat = repeat,
            settled = settled, status = status, detected = detected, sourceApp = if (detected) GCASH else null, createdAt = now)
    private val paidTransaction = rows.firstOrNull { it.type == TransactionType.EXPENSE && it.status == TransactionStatus.CONFIRMED }
    private val bills = MutableStateFlow(listOfNotNull(
        bill(1, "Meralco", 2410, 3, BillRepeat.MONTHLY),
        bill(2, "Maynilad", 680, 9, BillRepeat.MONTHLY, accountId = 2),
        bill(3, "Netflix", 549, 18, BillRepeat.MONTHLY),
        bill(4, "PhilHealth", 1200, 60, BillRepeat.YEARLY),
        bill(5, "Globe Postpaid", 1499, 12, BillRepeat.ONCE, status = TransactionStatus.NEEDS_REVIEW, detected = true),
        bill(6, "Converge Fiber", 1699, -2, BillRepeat.MONTHLY),
        paidTransaction?.let {
            bill(7, "Smart Postpaid", 0, 0, BillRepeat.MONTHLY, settled = 1).copy(amountMinor = it.amountMinor,
                startsOn = it.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
        },
    ))
    private val payments = MutableStateFlow(listOfNotNull(paidTransaction?.let {
        BillPayment(1, 7, it.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date, it.id, it.occurredAt)
    }))
    private var nextBillId = 8L
    private var nextPaymentId = 2L
    override fun observeBills() = bills
    override fun observePayments() = payments
    override suspend fun byId(id: Long) = bills.value.find { it.id == id }
    override suspend fun save(bill: Bill): Long {
        bill.validate()
        val id = bill.id.takeIf { it > 0 } ?: nextBillId++
        bills.value = bills.value.filterNot { it.id == id } + bill.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) {
        bills.value = bills.value.filterNot { it.id == id }
        payments.value = payments.value.filterNot { it.billId == id }
    }
    override suspend fun confirm(id: Long) {
        val draft = byId(id) ?: return
        val existing = bills.value.find { it.id != id && it.status == TransactionStatus.CONFIRMED && draft.sourceApp != null &&
            it.sourceApp == draft.sourceApp && it.name.equals(draft.name, ignoreCase = true) }
        if (existing == null) save(draft.copy(status = TransactionStatus.CONFIRMED))
        else { save(existing.copy(amountMinor = draft.amountMinor, startsOn = draft.startsOn, settled = 0, remindedFor = null)); delete(id) }
    }
    override suspend fun recordDetected(bill: Bill): Boolean {
        if (bills.value.any { it.sourceApp == bill.sourceApp && it.name.equals(bill.name, true) && it.startsOn == bill.startsOn }) return false
        save(bill.copy(id = 0)); return true
    }
    override suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?): Long {
        val bill = byId(billId) ?: return -1
        if (bill.nextDue != dueOn) return -1
        save(bill.copy(settled = bill.settled + 1))
        val id = nextPaymentId++
        payments.value = payments.value + BillPayment(id, billId, dueOn, transactionId, Clock.System.now())
        return id
    }
    override suspend fun unsettle(paymentId: Long) {
        val payment = payments.value.find { it.id == paymentId } ?: return
        payments.value = payments.value.filterNot { it.id == paymentId }
        byId(payment.billId)?.let { save(it.copy(settled = maxOf(0, it.settled - 1))) }
    }
    override suspend fun markReminded(id: Long, due: LocalDate) { byId(id)?.let { save(it.copy(remindedFor = due)) } }
}
