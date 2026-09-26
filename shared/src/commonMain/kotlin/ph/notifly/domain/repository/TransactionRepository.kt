package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>>
    /**
     * Observes confirmed rows at or after [since], newest occurrence first, then newest creation.
     * Supply a nonnegative [limit] to cap each emitted list; zero yields empty lists.
     */
    fun observeConfirmedSince(since: Instant, limit: Int): Flow<List<Transaction>>
    suspend fun byId(id: Long): Transaction?
    suspend fun upsert(transaction: Transaction): Long
    suspend fun delete(id: Long)
    /** Confirmed only. NEEDS_REVIEW must not move the headline balance. */
    fun observeConfirmedNetMinor(): Flow<Long>
}
