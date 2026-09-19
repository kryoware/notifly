package ph.notifly.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import kotlin.time.Clock

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
    override suspend fun byId(id: Long) = rows.value.find { it.id == id }
    override suspend fun upsert(transaction: Transaction): Long {
        val id = transaction.id.takeIf { it != 0L } ?: nextId++
        rows.value = (rows.value.filterNot { it.id == id } + transaction.copy(id = id)).sortedByDescending { it.occurredAt }
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
