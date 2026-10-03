package ph.notifly.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.repository.TransactionRepository
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BillsLinkTest {
    @Test fun rejectedLinkKeepsExpensePendingAndConfirmationFailureReversesSettlement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(emptyPreferences())
        }
        val transactions = DemoTransactions()
        val bills = DemoBills()
        val ledger = DemoLedger(transactions)
        val model = BillsModel(bills, transactions, ledger, AppPreferences(store))
        val pendingId = transactions.upsert(transactions.byId(2)!!.copy(id = 0, title = "[TEST] bill link expense"))
        val pending = transactions.byId(pendingId)!!
        val bill = bills.byId(1)!!
        val due = bill.nextDue!!
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect() }
        try {
            bills.settle(bill.id, due, null)
            model.link(bill, due, pending)
            runCurrent()
            assertEquals(TransactionStatus.NEEDS_REVIEW, transactions.byId(pending.id)!!.status)
            assertNull(bills.observePayments().first().single().transactionId)
            bills.unsettle(bills.observePayments().first().single().id)
            val failing = object : TransactionRepository by transactions {
                override suspend fun upsert(transaction: Transaction): Long = throw IllegalStateException("write failed")
            }
            val failedModel = BillsModel(bills, failing, ledger, AppPreferences(store))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { failedModel.events.collect() }
            failedModel.link(bill, due, pending)
            runCurrent()
            assertTrue(bills.observePayments().first().isEmpty())
            assertEquals(TransactionStatus.NEEDS_REVIEW, transactions.byId(pending.id)!!.status)
            failedModel.viewModelScope.cancel()
            model.link(bill, due, pending)
            runCurrent()
            assertEquals(TransactionStatus.CONFIRMED, transactions.byId(pending.id)!!.status)
            assertEquals(pending.id, bills.observePayments().first().single().transactionId)
        } finally {
            model.viewModelScope.cancel()
            transactions.delete(pendingId)
            Dispatchers.resetMain()
        }
    }
}
