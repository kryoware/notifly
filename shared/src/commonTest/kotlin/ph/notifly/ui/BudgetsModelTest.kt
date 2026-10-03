package ph.notifly.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.Category
import ph.notifly.domain.model.TransactionType
import ph.notifly.domain.repository.LedgerRepository
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsModelTest {
    private fun preferences() = AppPreferences(object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    })

    @Test fun navigationDuringSaveStillWritesEveryCategory() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = preferences()
            preferences.setMonthlyBudget(10_000)
            val delegate = DemoLedger(DemoTransactions())
            val categories = delegate.observeCategories().first().filter { it.type == TransactionType.EXPENSE }.take(2)
            categories.forEach { delegate.saveCategory(it.copy(budgetMinor = 5_000)) }
            val resume = CompletableDeferred<Unit>()
            val ledger = object : LedgerRepository by delegate {
                override suspend fun saveCategory(category: Category): Long {
                    resume.await()
                    return delegate.saveCategory(category)
                }
            }
            val model = BudgetsModel(preferences, ledger)
            val save = model.save(4_000, categories.associate { it.id to 2_000L })
            runCurrent()
            assertEquals(10_000L, preferences.monthlyBudget.first())
            save.cancel()
            resume.complete(Unit)
            save.join()
            runCurrent()
            val saved = delegate.observeCategories().first().filter { it.id in categories.map(Category::id) }
            assertTrue(saved.all { it.budgetMinor == 2_000L })
            assertEquals(4_000L, saved.sumOf { it.budgetMinor ?: 0L })
            assertEquals(4_000L, preferences.monthlyBudget.first())
            model.viewModelScope.cancel()
            runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun categoryFailureRestoresEarlierWritesAndLeavesMonthlyCapUnchanged() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = preferences()
            preferences.setMonthlyBudget(10_000)
            val delegate = DemoLedger(DemoTransactions())
            val categories = delegate.observeCategories().first().filter { it.type == TransactionType.EXPENSE }.take(2)
            categories.forEachIndexed { index, category -> delegate.saveCategory(category.copy(budgetMinor = 5_000L + index * 1_000L)) }
            val initial = categories.map { category -> delegate.observeCategories().first().first { it.id == category.id } }
            val ledger = object : LedgerRepository by delegate {
                override suspend fun saveCategory(category: Category): Long {
                    if (category.id == initial[1].id && category.budgetMinor == 2_000L) error("write failed")
                    return delegate.saveCategory(category)
                }
            }
            val model = BudgetsModel(preferences, ledger)
            model.save(4_000, initial.associate { it.id to 2_000L }).join()
            val saved = delegate.observeCategories().first().filter { it.id in initial.map(Category::id) }
            assertEquals(initial.associate { it.id to it.budgetMinor }, saved.associate { it.id to it.budgetMinor })
            assertEquals(10_000L, preferences.monthlyBudget.first())
            model.viewModelScope.cancel()
            runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun renamedCategoryKeepsItsEditAndSameNameReplacementDoesNotReceiveIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val ledger = DemoLedger(DemoTransactions())
            val original = ledger.observeCategories().first().first { it.name == "Food" }
            val model = BudgetsModel(preferences(), ledger)
            val save = model.save(5_000, mapOf(original.id to 2_000L))
            ledger.saveCategory(original.copy(name = "Meals"))
            val replacement = ledger.saveCategory(Category(name = "Food", type = TransactionType.EXPENSE))
            save.join()
            val saved = ledger.observeCategories().first()
            assertEquals(2_000L, saved.first { it.id == original.id }.budgetMinor)
            assertEquals("Meals", saved.first { it.id == original.id }.name)
            assertNull(saved.first { it.id == replacement }.budgetMinor)
            model.viewModelScope.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun missingCategoryRejectsSaveBeforeChangingCap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = preferences()
            preferences.setMonthlyBudget(10_000)
            val model = BudgetsModel(preferences, DemoLedger(DemoTransactions()))
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.events.first() }
            model.save(2_000, mapOf(Long.MAX_VALUE to 1_000L)).join()
            assertEquals(10_000L, preferences.monthlyBudget.first())
            assertEquals(UiEvent.Message("A category no longer exists. Reload budgets and try again."), event.await())
            model.viewModelScope.cancel()
        } finally { Dispatchers.resetMain() }
    }
}
