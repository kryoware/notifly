package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ph.notifly.domain.model.AllowedApp

class FakeAllowListRepository : AllowListRepository {

    private val store = MutableStateFlow<List<AllowedApp>>(emptyList())

    override fun observeAll(): Flow<List<AllowedApp>> = store

    override suspend fun isAllowed(packageName: String): Boolean =
        store.value.any { it.packageName == packageName && it.listening }

    override suspend fun setListening(packageName: String, listening: Boolean) {
        store.update { list ->
            list.map {
                if (it.packageName == packageName) it.copy(listening = listening) else it
            }
        }
    }

    override suspend fun setFinance(packageName: String, finance: Boolean) {
        store.update { list -> list.map { if (it.packageName == packageName) it.copy(finance = finance) else it } }
    }

    override suspend fun incrementCapturedCount(packageName: String) {
        store.update { list ->
            list.map {
                if (it.packageName == packageName) it.copy(capturedCount = it.capturedCount + 1) else it
            }
        }
    }

    /** Helper for seeding test data. */
    fun seed(vararg apps: AllowedApp) {
        store.value = apps.toList()
    }
}
