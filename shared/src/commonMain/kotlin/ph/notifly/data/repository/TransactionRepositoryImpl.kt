package ph.notifly.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import ph.notifly.domain.repository.LedgerRepository
import ph.notifly.data.local.TransactionDao
import ph.notifly.data.local.toDomain
import ph.notifly.data.local.toEntity
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.repository.TransactionRepository

class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    private val ledger: LedgerRepository? = null,
) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().onStart { ledger?.initialize() }.map { entities -> entities.map { it.toDomain() } }

    override fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        dao.observeByStatus(status.name).onStart { ledger?.initialize() }.map { entities -> entities.map { it.toDomain() } }

    override fun observeConfirmedSince(since: kotlin.time.Instant, limit: Int): Flow<List<Transaction>> =
        dao.observeConfirmedSince(since.toEpochMilliseconds(), limit).onStart { ledger?.initialize() }.map { entities -> entities.map { it.toDomain() } }

    override suspend fun byId(id: Long): Transaction? { ledger?.initialize(); return dao.byId(id)?.toDomain() }

    /**
     * Saves a PHP transaction and applies the corresponding local sync-queue update.
     *
     * @throws IllegalArgumentException if [transaction] uses another currency.
     */
    override suspend fun upsert(transaction: Transaction): Long {
        ledger?.initialize()
        require(transaction.currency == "PHP") { "Only PHP transactions are supported" }
        return dao.saveLocally(transaction.toEntity())
    }

    override suspend fun delete(id: Long) =
        dao.deleteLocally(id)

    override suspend fun importTransactions(transactions: List<Transaction>): Int {
        ledger?.initialize(); return dao.importDrafts(transactions.map { it.toEntity() })
    }

    override fun observeConfirmedNetMinor(): Flow<Long> =
        dao.observeConfirmedNetMinor()
}
