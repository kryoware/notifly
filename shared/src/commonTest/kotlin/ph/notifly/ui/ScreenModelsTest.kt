package ph.notifly.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import ph.notifly.domain.model.TransactionStatus
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenModelsTest {
    @Test fun reviewDoesNotMoveBalanceAndInvalidEditsDoNotWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = DemoTransactions()
            assertEquals(4800000L, repository.observeConfirmedNetMinor().first())
            val editor = EditorModel(repository, 2)
            runCurrent()
            editor.edit(title = "", amount = "0")
            editor.save()
            runCurrent()
            assertNotNull(editor.state.value.error)
            assertEquals(TransactionStatus.NEEDS_REVIEW, repository.byId(2)?.status)
            editor.edit(title = "Groceries", amount = "12.34")
            editor.save()
            runCurrent()
            assertEquals(TransactionStatus.CONFIRMED, repository.byId(2)?.status)
            assertEquals(4798766L, repository.observeConfirmedNetMinor().first())
            repository.delete(2)
            assertEquals(4800000L, repository.observeConfirmedNetMinor().first())
        } finally { Dispatchers.resetMain() }
    }
}
