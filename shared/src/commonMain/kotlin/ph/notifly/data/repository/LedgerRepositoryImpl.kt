package ph.notifly.data.repository

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.notifly.data.local.*
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.LedgerRepository

class LedgerRepositoryImpl(private val dao: LedgerDao, private val preferences: AppPreferences) : LedgerRepository {
    private val initialization = Mutex()
    private var ready = false
    override suspend fun initialize() = initialization.withLock {
        if (!ready) {
            dao.importPreferences(preferences.accountBalances.first(), preferences.categoryBudgets.first())
            preferences.clearLegacyLedgerPreferences()
            ready = true
        }
    }
    override fun observeAccounts() = combine(dao.observeAccounts(), dao.observeLinks()) { accounts, links ->
        accounts.map { it.toDomain(links) }
    }.onStart { initialize() }
    override fun observeCategories() = dao.observeCategories().map { it.map(CategoryEntity::toDomain) }.onStart { initialize() }
    override fun observeDrafts() = dao.observeDrafts().map { it.map(CapturedDraftEntity::toDomain) }.onStart { initialize() }
    override suspend fun saveAccount(account: Account): Long { initialize(); return dao.saveAccount(account) }
    override suspend fun deleteAccount(id: Long) { initialize(); dao.deleteAccount(id) }
    override suspend fun saveCategory(category: Category): Long { initialize(); return dao.saveCategory(category) }
    override suspend fun deleteDraft(id: Long) { initialize(); dao.deleteDraft(id) }
    override suspend fun confirmDraft(id: Long, transaction: Transaction): Long {
        initialize(); return dao.confirmDraft(id, transaction.toEntity())
    }
}
