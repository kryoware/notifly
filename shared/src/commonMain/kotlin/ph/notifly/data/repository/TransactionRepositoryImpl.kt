package ph.notifly.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.notifly.data.local.TransactionDao
import ph.notifly.data.local.toDomain
import ph.notifly.data.local.toEntity
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.repository.TransactionRepository

class TransactionRepositoryImpl(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        dao.observeByStatus(status.name).map { entities -> entities.map { it.toDomain() } }

    override suspend fun byId(id: Long): Transaction? =
        dao.byId(id)?.toDomain()

    override suspend fun upsert(transaction: Transaction): Long {
        require(transaction.currency == "PHP") { "Only PHP transactions are supported" }
        return dao.upsert(transaction.toEntity())
    }

    override suspend fun delete(id: Long) =
        dao.delete(id)

    override fun observeConfirmedNetMinor(): Flow<Long> =
        dao.observeConfirmedNetMinor()
}
