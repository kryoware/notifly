package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.AllowedApp

interface AllowListRepository {
    fun observeAll(): Flow<List<AllowedApp>>
    suspend fun isAllowed(packageName: String): Boolean
    suspend fun setListening(packageName: String, listening: Boolean)
    /** Marks an existing package for account estimates and transfer detection without changing listening. */
    suspend fun setFinance(packageName: String, finance: Boolean)
    suspend fun incrementCapturedCount(packageName: String)
}
