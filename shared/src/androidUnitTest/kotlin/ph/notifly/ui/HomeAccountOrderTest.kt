package ph.notifly.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okio.IOException
import org.junit.Test
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.Account
import ph.notifly.domain.model.AccountType
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAccountOrderTest {
    @Test fun movesPersistAndFailedMoveRestoresLastSavedOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = object : DataStore<Preferences> {
            val state = MutableStateFlow(emptyPreferences())
            var fail = false
            var gate: CompletableDeferred<Unit>? = null
            override val data = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                gate?.await()
                if (fail) throw IOException("write failed")
                return transform(state.value).also { state.value = it }
            }
        }
        val transactions = DemoTransactions()
        val ledger = DemoLedger(transactions)
        val prefs = AppPreferences(store)
        val model = HomeModel(transactions, DemoAllowList(), prefs, ledger)
        val messages = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { messages.add(it) } }
        try {
            runCurrent()
            val net = model.state.value.net
            assertEquals(listOf(1L, 2L), model.state.value.accounts.map { it.account.id })
            model.reorderAccounts(listOf(2L, 1L))
            runCurrent()
            assertEquals(listOf(2L, 1L), prefs.homeAccountOrder.first())
            assertEquals(listOf(2L, 1L), model.state.value.accounts.map { it.account.id })
            assertEquals(false, model.savingOrder.first())

            store.fail = true
            store.gate = CompletableDeferred()
            model.reorderAccounts(listOf(1L, 2L))
            runCurrent()
            assertEquals(true, model.savingOrder.first())
            assertEquals(listOf(1L, 2L), model.state.value.accounts.map { it.account.id })
            model.reorderAccounts(listOf(2L, 1L)) // A pending write cannot be overtaken.
            store.gate!!.complete(Unit)
            runCurrent()
            assertEquals(listOf(2L, 1L), prefs.homeAccountOrder.first())
            assertEquals(listOf(2L, 1L), model.state.value.accounts.map { it.account.id })
            assertEquals(false, model.savingOrder.first())
            assertTrue(messages.last() is UiEvent.Message)
            assertEquals(net, model.state.value.net)

            ledger.saveAccount(ledger.observeAccounts().first().first { it.id == 2L }.copy(name = "A renamed wallet", archived = true))
            ledger.saveAccount(Account(3, "New account", AccountType.BANK))
            runCurrent()
            assertEquals(listOf(2L, 1L, 3L), model.state.value.accounts.map { it.account.id })

            val reopened = HomeModel(transactions, DemoAllowList(), AppPreferences(store), ledger)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reopened.state.collect() }
            runCurrent()
            assertEquals(listOf(2L, 1L, 3L), reopened.state.value.accounts.map { it.account.id })
            reopened.viewModelScope.cancel()
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
