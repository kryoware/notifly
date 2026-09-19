package ph.notifly.data.local

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ph.notifly.data.repository.TransactionRepositoryImpl
import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class SyncQueueTest {
    @Test fun onlyConfirmedRowsQueueAndStaleAckCannotLoseEdits() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver()).build()
        try {
            val dao = db.transactionDao()
            val repository = TransactionRepositoryImpl(dao)
            val draft = Transaction(title = "Merchant", amountMinor = 100, type = TransactionType.EXPENSE,
                status = TransactionStatus.NEEDS_REVIEW, category = "Other", occurredAt = Clock.System.now(), sourceApp = "wallet", captureId = 5,
                note = "device-only note")
            val id = repository.upsert(draft)
            assertEquals(0, dao.observePendingCount().first())
            assertFailsWith<IllegalArgumentException> { draft.toSyncTransaction() }
            val confirmed = draft.copy(id = id, status = TransactionStatus.CONFIRMED)
            repository.upsert(confirmed)
            val first = dao.pendingChanges().single()
            repository.upsert(confirmed.copy(amountMinor = 200))
            dao.acknowledge(first.id)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(200L, repository.byId(id)!!.toSyncTransaction().amountMinor)
            assertFalse(repository.byId(id)!!.toSyncTransaction().toString().contains("device-only"))
            repository.delete(id)
            assertEquals("DELETE", dao.pendingChanges().single().operation)
            assertNull(repository.byId(id))
            repository.upsert(confirmed)
            assertEquals("UPSERT", dao.pendingChanges().single().operation)
        } finally { db.close() }
    }
}
