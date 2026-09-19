package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>>
    suspend fun byId(id: Long): Transaction?
    suspend fun upsert(transaction: Transaction): Long
    suspend fun delete(id: Long)
    /** Confirmed only. NEEDS_REVIEW must not move the headline balance. */
    fun observeConfirmedNetMinor(): Flow<Long>
}
