package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.*

interface LedgerRepository {
    suspend fun initialize()
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>
    fun observeDrafts(): Flow<List<CapturedDraft>>
    suspend fun saveAccount(account: Account): Long
    suspend fun deleteAccount(id: Long)
    suspend fun saveCategory(category: Category): Long
    suspend fun deleteDraft(id: Long)
    suspend fun confirmDraft(id: Long, transaction: Transaction): Long
}
