package ph.notifly.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import kotlin.time.Clock
import kotlin.time.Instant

/** Isolated, explicitly selected demo data; never inserted into the user's ledger. */
class DemoTransactions : TransactionRepository {
    private val rows = MutableStateFlow(listOf(
        Transaction(1, "ACME CORP", 4800000, type = TransactionType.INCOME, status = TransactionStatus.CONFIRMED,
            category = "Income", occurredAt = Clock.System.now(), sourceApp = "GCash", captureId = null),
        Transaction(2, "SM Supermarket", 245050, type = TransactionType.EXPENSE, status = TransactionStatus.NEEDS_REVIEW,
            category = "Shopping", occurredAt = Clock.System.now(), sourceApp = "GCash", captureId = null),
    ))
    private var nextId = 3L
    override fun observeAll() = rows
    override fun observeByStatus(status: TransactionStatus) = rows.map { it.filter { t -> t.status == status } }
    override fun observeConfirmedSince(since: Instant, limit: Int) = rows.map { values ->
        values.filter { it.status == TransactionStatus.CONFIRMED && it.occurredAt >= since }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.createdAt })
            .take(limit)
    }
    override suspend fun byId(id: Long) = rows.value.find { it.id == id }
    override suspend fun upsert(transaction: Transaction): Long {
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
        AllowedApp("com.globe.gcash.android", "GCash", "Wallet", true),
        AllowedApp("com.paymaya", "Maya", "Wallet", true),
        AllowedApp("com.bpi.ng.app", "BPI Mobile", "Bank", false),
    ))
    override fun observeAll() = rows
    override suspend fun isAllowed(packageName: String) = rows.value.any { it.packageName == packageName && it.listening }
    override suspend fun setListening(packageName: String, listening: Boolean) {
        rows.value = rows.value.map { if (it.packageName == packageName) it.copy(listening = listening) else it }
    }
    override suspend fun incrementCapturedCount(packageName: String) {
        rows.value = rows.value.map { if (it.packageName == packageName) it.copy(capturedCount = it.capturedCount + 1) else it }
    }
}

class DemoCaptures : CaptureRepository {
    private val transactions = DemoTransactions()
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
        list.filter { it.result != CaptureResult.IGNORED && (filter == null || it.result == filter) }.sortedByDescending { it.capturedAt }
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
