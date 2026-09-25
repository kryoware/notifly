package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType
import kotlin.time.Instant

class FakeTransactionRepository : TransactionRepository {

    private val store = MutableStateFlow<List<Transaction>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Transaction>> = store

    override fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        store.map { list -> list.filter { it.status == status } }

    override fun observeConfirmedSince(since: Instant, limit: Int): Flow<List<Transaction>> =
        store.map { list ->
            list.filter { it.status == TransactionStatus.CONFIRMED && it.occurredAt >= since }
                .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.createdAt })
                .take(limit)
        }

    override suspend fun byId(id: Long): Transaction? =
        store.value.firstOrNull { it.id == id }

    override suspend fun upsert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        val updated = transaction.copy(id = id)
        store.update { list ->
            list.filterNot { it.id == id } + updated
        }
        return id
    }

    override suspend fun delete(id: Long) {
        store.update { list -> list.filterNot { it.id == id } }
    }

    override fun observeConfirmedNetMinor(): Flow<Long> =
        store.map { list ->
            list.filter { it.status == TransactionStatus.CONFIRMED }
                .sumOf { txn ->
                    when (txn.type) {
                        TransactionType.INCOME -> txn.amountMinor
                        TransactionType.EXPENSE -> -txn.amountMinor
                        TransactionType.TRANSFER -> 0L
                    }
                }
        }
}
