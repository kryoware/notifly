package ph.notifly.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ph.notifly.domain.model.TransactionStatus
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenModelsTest {
    @Test fun manualEntryFromLogDoesNotCopyRawTextIntoTransaction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val transactions = DemoTransactions()
            val captures = DemoCaptures()
            val editor = EditorModel(transactions, 0, captures, 3)
            runCurrent()
            assertNotNull(editor.state.value.sourceText)
            editor.edit(title = "Manual payment", amount = "25")
            editor.save()
            runCurrent()
            val saved = transactions.observeAll().first().first { it.title == "Manual payment" }
            assertEquals("", saved.note)
            assertEquals("GCash", saved.sourceApp)
            captures.redactBodies()
            assertTrue(captures.observeLog().first().all { it.body == null })
        } finally { Dispatchers.resetMain() }
    }
    @Test fun onboardingCapsAtFourthPage() {
        val model = OnboardingModel()
        repeat(4) { model.next() }
        assertEquals(3, model.state.value.page)
    }
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
            editor.edit(date = "2026-02-30")
            editor.save()
            assertNotNull(editor.state.value.error)
            editor.edit(date = "2026-09-20")
            editor.save()
            runCurrent()
            assertEquals(TransactionStatus.CONFIRMED, repository.byId(2)?.status)
            assertEquals(4798766L, repository.observeConfirmedNetMinor().first())
            repository.delete(2)
            assertEquals(4800000L, repository.observeConfirmedNetMinor().first())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun timePickerEditIsPreservedOnSave() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = DemoTransactions()
            val editor = EditorModel(repository, 2)
            runCurrent()
            editor.edit(time = "14:30")
            editor.save()
            runCurrent()
            val saved = repository.byId(2)!!
            val local = saved.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault())
            assertEquals(14, local.hour)
            assertEquals(30, local.minute)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun newTransactionDefaultsToMidnightWhenTimeNotSupplied() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = DemoTransactions()
            val editor = EditorModel(repository, 0)
            runCurrent()
            assertEquals("00:00", editor.state.value.time)
            editor.edit(title = "Coffee", amount = "5")
            editor.save()
            runCurrent()
            val saved = repository.observeAll().first().first { it.title == "Coffee" }
            val local = saved.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault())
            assertEquals(0, local.hour)
            assertEquals(0, local.minute)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun confirmSetsConfirmedAndUndoRestoresNeedsReview() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = DemoTransactions()
            val model = TransactionsModel(repository)
            val t = repository.byId(2)!!
            assertEquals(TransactionStatus.NEEDS_REVIEW, t.status)
            model.confirm(t)
            runCurrent()
            assertEquals(TransactionStatus.CONFIRMED, repository.byId(2)?.status)
            // confirm() emits UiEvent.Message(undo = t); re-upserting the pre-confirm transaction is the undo path.
            repository.upsert(t)
            assertEquals(TransactionStatus.NEEDS_REVIEW, repository.byId(2)?.status)
            model.viewModelScope.cancel()
            runCurrent()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun bulkActionsConfirmPendingAndDeleteSelectedRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = DemoTransactions()
            val model = TransactionsModel(repository)
            val rows = repository.observeAll().first()
            model.confirmAll(rows)
            runCurrent()
            assertTrue(repository.observeAll().first().all { it.status == TransactionStatus.CONFIRMED })
            model.deleteAll(rows)
            runCurrent()
            assertTrue(repository.observeAll().first().isEmpty())
            model.viewModelScope.cancel()
        } finally { Dispatchers.resetMain() }
    }
}
