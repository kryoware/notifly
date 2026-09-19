package ph.notifly.data.local

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    private fun buildDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Test
    fun `insert transaction and read it back as domain model`() = runTest {
        val db = buildDatabase()
        val transaction = Transaction(
            title = "Payroll",
            amountMinor = 4_800_000,
            type = TransactionType.INCOME,
            status = TransactionStatus.NEEDS_REVIEW,
            category = "Salary",
            occurredAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            sourceApp = "com.gcash.app",
            captureId = null,
        )

        val id = db.transactionDao().upsert(transaction.toEntity())
        val stored = db.transactionDao().byId(id)?.toDomain()

        assertEquals(transaction.copy(id = id), stored)
    }

    @Test
    fun `query by status excludes other statuses`() = runTest {
        val db = buildDatabase()
        val needsReview = Transaction(
            title = "Needs review",
            amountMinor = 100,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.NEEDS_REVIEW,
            category = "Misc",
            occurredAt = Instant.fromEpochMilliseconds(0),
            sourceApp = null,
            captureId = null,
        )
        val confirmed = needsReview.copy(status = TransactionStatus.CONFIRMED)
        db.transactionDao().upsert(needsReview.toEntity())
        db.transactionDao().upsert(confirmed.toEntity())

        val result = db.transactionDao().observeByStatus(TransactionStatus.CONFIRMED.name).first()

        assertEquals(1, result.size)
        assertEquals(TransactionStatus.CONFIRMED, result.first().toDomain().status)
    }

    @Test
    fun `delete removes the transaction`() = runTest {
        val db = buildDatabase()
        val id = db.transactionDao().upsert(
            Transaction(
                title = "Gone soon",
                amountMinor = 500,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.CONFIRMED,
                category = "Misc",
                occurredAt = Instant.fromEpochMilliseconds(0),
                sourceApp = null,
                captureId = null,
            ).toEntity(),
        )

        db.transactionDao().delete(id)

        assertNull(db.transactionDao().byId(id))
    }
}
