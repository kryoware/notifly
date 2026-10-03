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
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class TransferFeePersistenceTest {
    @Test fun databaseRoundTripAndConfirmedNetIncludeOnlyFee() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver()).build()
        try {
            db.seedTestAccounts()
            val repository = TransactionRepositoryImpl(db.transactionDao())
            val row = Transaction(title = "Transfer", amountMinor = 10000, type = TransactionType.TRANSFER,
                status = TransactionStatus.NEEDS_REVIEW, category = "Transfer",
                occurredAt = Instant.parse("2026-10-03T12:00:00Z"), sourceApp = null, captureId = null,
                accountId = 1, toAccountId = 2, feeMinor = 1500)
            val id = repository.upsert(row)
            assertEquals(row.copy(id = id), repository.byId(id))
            assertEquals(0L, repository.observeConfirmedNetMinor().first())
            repository.upsert(row.copy(id = id, status = TransactionStatus.CONFIRMED))
            assertEquals(-1500L, repository.observeConfirmedNetMinor().first())
            assertFailsWith<IllegalArgumentException> { repository.upsert(row.copy(feeMinor = -1)) }
            assertFailsWith<IllegalArgumentException> { repository.upsert(row.copy(amountMinor = Long.MAX_VALUE)) }
        } finally { db.close() }
    }
}
