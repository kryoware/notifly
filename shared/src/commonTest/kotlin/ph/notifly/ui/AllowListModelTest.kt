package ph.notifly.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.repository.FakeAllowListRepository
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AllowListModelTest {
    private val apps = arrayOf(
        AllowedApp("bank.z", "Bank", "Bank", false),
        AllowedApp("bank.a", "Bank", "Bank", true, finance = true),
        AllowedApp("wallet.bank", "Wallet", "Wallet", true),
        AllowedApp("other", "Other", "Other", false),
    )

    private fun checkModel(finance: Boolean = false, check: suspend TestScope.(AllowListModel, FakeAllowListRepository) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAllowListRepository().apply { seed(*apps) }
        val model = AllowListModel(repository, finance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        try { runCurrent(); check(model, repository) }
        finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun debounceCancelsPreviousSearchAndSuppressesStaleSuggestions() = checkModel { model, _ ->
        model.search("bank")
        runCurrent(); advanceTimeBy(150); runCurrent()
        assertEquals("bank", model.state.value.query)
        assertEquals(listOf("bank.a", "bank.z", "wallet.bank"), model.state.value.suggestions.map { it.packageName })
        model.search("wal"); runCurrent()
        assertTrue(model.state.value.suggestions.isEmpty())
        advanceTimeBy(100)
        model.search("other"); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals("bank", model.state.value.query)
        advanceTimeBy(100); runCurrent()
        assertEquals("other", model.state.value.query)
        assertEquals(listOf("other"), model.state.value.suggestions.map { it.packageName })
    }

    @Test fun clearingAppliesImmediatelyAndCancelsPendingQuery() = checkModel { model, _ ->
        model.search("bank"); runCurrent(); advanceTimeBy(150); runCurrent()
        model.search("other"); runCurrent(); advanceTimeBy(100)
        model.search(""); runCurrent()
        assertEquals("", model.state.value.query)
        assertEquals(4, model.state.value.apps.size)
        assertTrue(model.state.value.suggestions.isEmpty())
        advanceTimeBy(200); runCurrent()
        assertEquals("", model.state.value.query)
    }

    @Test fun selectionNarrowsWithoutEnablingAndEditingResetsIt() = checkModel { model, repository ->
        model.search("bank"); runCurrent(); advanceTimeBy(150); runCurrent()
        model.selectSuggestion("bank.z"); runCurrent()
        assertEquals("bank.z", model.state.value.selectedPackage)
        assertEquals(listOf("bank.z"), model.state.value.apps.map { it.packageName })
        assertFalse(repository.isAllowed("bank.z"))
        assertTrue(model.state.value.suggestions.isEmpty())
        model.search("ban"); runCurrent(); advanceTimeBy(150); runCurrent()
        assertNull(model.state.value.selectedPackage)
        assertEquals(3, model.state.value.apps.size)
        model.selectSuggestion("bank.z"); runCurrent()
        model.search(""); runCurrent()
        assertNull(model.state.value.selectedPackage)
        assertEquals(4, model.state.value.apps.size)
    }

    @Test fun financeSuggestionsAndSelectionsRequireAllowedApps() = checkModel(finance = true) { model, repository ->
        model.search("bank"); runCurrent(); advanceTimeBy(150); runCurrent()
        assertEquals(listOf("bank.a", "wallet.bank"), model.state.value.suggestions.map { it.packageName })
        model.selectSuggestion("bank.z"); runCurrent()
        assertNull(model.state.value.selectedPackage)
        model.selectSuggestion("wallet.bank"); runCurrent()
        assertFalse(model.state.value.apps.single().finance)
        repository.setListening("wallet.bank", false); runCurrent()
        assertTrue(model.state.value.apps.isEmpty())
    }

    @Test fun suggestionsAreLimitedAndPinnedListOrderSurvivesSearch() = checkModel { model, repository ->
        repository.seed(*apps, *(1..6).map { AllowedApp("bank.$it", "Bank $it", "Bank", false) }.toTypedArray())
        runCurrent()
        model.search("bank"); runCurrent(); advanceTimeBy(150); runCurrent()
        assertEquals(5, model.state.value.suggestions.size)
        assertEquals("bank.a", model.state.value.apps.first().packageName)
        model.toggle(model.state.value.apps.first { it.packageName == "bank.z" }).join(); runCurrent()
        model.search(""); runCurrent()
        assertEquals("bank.a", model.state.value.apps.first().packageName)
    }
}
