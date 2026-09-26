package ph.notifly.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.notifly.data.local.AllowedAppDao
import ph.notifly.data.local.toDomain
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.repository.AllowListRepository

class AllowListRepositoryImpl(
    private val dao: AllowedAppDao,
) : AllowListRepository {

    override fun observeAll(): Flow<List<AllowedApp>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun isAllowed(packageName: String): Boolean =
        dao.isAllowed(packageName)

    override suspend fun setListening(packageName: String, listening: Boolean) =
        dao.setListening(packageName, listening)

    override suspend fun setFinance(packageName: String, finance: Boolean) =
        dao.setFinance(packageName, finance)

    override suspend fun incrementCapturedCount(packageName: String) =
        dao.incrementCapturedCount(packageName)
}
