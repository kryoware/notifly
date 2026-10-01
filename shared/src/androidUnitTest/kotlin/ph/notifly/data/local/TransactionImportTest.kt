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
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class TransactionImportTest {
    @Test fun importsAreAtomicAppendOnlyDeduplicatedDrafts() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver()).build()
        try {
            db.seedTestAccounts()
            val dao = db.transactionDao()
            val repository = TransactionRepositoryImpl(dao)
            val original = Transaction(title = "Salary", amountMinor = 10000, type = TransactionType.INCOME,
                status = TransactionStatus.CONFIRMED, category = "Income",
                occurredAt = Instant.parse("2026-10-01T12:00:00Z"), sourceApp = "wallet", captureId = 7, accountId = 1)
            val id = repository.upsert(original)
            val new = original.copy(id = id, title = "Another transaction")
            val csv = TransactionCsv.encode(listOf(original, new, new))
            assertEquals(1, repository.importTransactions(TransactionCsv.decode(csv).map { it.copy(accountId = 1) }))
            assertEquals(2, repository.observeAll().first().size)
            assertEquals(original.copy(id = id, categoryId = repository.byId(id)?.categoryId), repository.byId(id))
            val imported = repository.observeAll().first().single { it.id != id }
            assertEquals(TransactionStatus.NEEDS_REVIEW, imported.status)
            assertNull(imported.captureId)
            assertEquals(10000L, repository.observeConfirmedNetMinor().first())
            assertEquals(1, dao.pendingChanges().size)
            assertEquals(0, repository.importTransactions(TransactionCsv.decode(csv).map { it.copy(accountId = 1) }))
            repository.upsert(imported.copy(status = TransactionStatus.CONFIRMED))
            assertEquals(0, repository.importTransactions(TransactionCsv.decode(csv).map { it.copy(accountId = 1) }))
            val before = repository.observeAll().first()
            assertFailsWith<IllegalArgumentException> {
                repository.importTransactions(listOf(new.copy(title = "Should roll back"), new.copy(currency = "USD")))
            }
            assertEquals(before, repository.observeAll().first())
        } finally { db.close() }
    }
}
