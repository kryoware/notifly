package ph.notifly.data.local

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import ph.notifly.data.repository.TransactionRepositoryImpl
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDate
import ph.notifly.domain.model.BillRepeat

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    private suspend fun buildDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .build()
            .also { it.seedTestAccounts() }

    @Test
    fun `insert transaction and read it back as domain model`() = runTest {
        val db = buildDatabase()
        db.seedTestAccounts()
        val transaction = Transaction(
            title = "Payroll",
            amountMinor = 4_800_000,
            type = TransactionType.INCOME,
            status = TransactionStatus.NEEDS_REVIEW,
            category = "Salary",
            occurredAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            sourceApp = "com.gcash.app",
            captureId = null, accountId = 1)

        val id = db.transactionDao().upsert(transaction.toEntity())
        val stored = db.transactionDao().byId(id)?.toDomain()

        assertEquals(transaction.copy(id = id), stored)
    }

    @Test
    fun `query by status excludes other statuses`() = runTest {
        val db = buildDatabase()
        db.seedTestAccounts()
        val needsReview = Transaction(
            title = "Needs review",
            amountMinor = 100,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.NEEDS_REVIEW,
            category = "Misc",
            occurredAt = Instant.fromEpochMilliseconds(0),
            sourceApp = null,
            captureId = null, accountId = 1)
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
        db.seedTestAccounts()
        val id = db.transactionDao().upsert(
            Transaction(
                title = "Gone soon",
                amountMinor = 500,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.CONFIRMED,
                category = "Misc",
                occurredAt = Instant.fromEpochMilliseconds(0),
                sourceApp = null,
                captureId = null, accountId = 1).toEntity(),
        )

        db.transactionDao().delete(id)

        assertNull(db.transactionDao().byId(id))
    }

    @Test
    fun `repository rejects non-PHP transactions`() = runTest {
        val db = buildDatabase()
        db.seedTestAccounts()
        val repository = TransactionRepositoryImpl(db.transactionDao())
        val transaction = Transaction(
            title = "Dollar transaction",
            amountMinor = 500,
            currency = "USD",
            type = TransactionType.EXPENSE,
            status = TransactionStatus.CONFIRMED,
            category = "Misc",
            occurredAt = Instant.fromEpochMilliseconds(0),
            sourceApp = null,
            captureId = null, accountId = 1)

        assertFailsWith<IllegalArgumentException> { repository.upsert(transaction) }
        assertEquals(emptyList(), db.transactionDao().observeAll().first())
    }

    @Test
    fun `confirmed net excludes legacy non-PHP rows`() = runTest {
        val db = buildDatabase()
        db.seedTestAccounts()
        val dao = db.transactionDao()
        val base = Transaction(
            title = "Income",
            amountMinor = 10_000,
            type = TransactionType.INCOME,
            status = TransactionStatus.CONFIRMED,
            category = "Misc",
            occurredAt = Instant.fromEpochMilliseconds(0),
            sourceApp = null,
            captureId = null, accountId = 1)
        dao.upsert(base.toEntity())
        dao.upsert(base.copy(title = "Legacy dollar income", amountMinor = 99_000, currency = "USD").toEntity())

        assertEquals(10_000, TransactionRepositoryImpl(dao).observeConfirmedNetMinor().first())
    }

    @Test
    fun `selected later occurrence is recorded and transaction deletion reverses payment and queues sync delete`() = runTest {
        val db = buildDatabase()
        try {
            val billDao = db.billDao()
            val startsOn = LocalDate(2026, 1, 15)
            val billId = billDao.save(BillEntity(name = "Electricity", amountMinor = 20_000, category = "Bills", accountId = 1,
                startsOnDay = startsOn.toEpochDays(), repeats = BillRepeat.MONTHLY.name, settled = 0,
                status = TransactionStatus.CONFIRMED.name, detected = false, sourceApp = null, captureId = null,
                remindedForDay = null, createdAtMillis = 0))
            val repository = TransactionRepositoryImpl(db.transactionDao(), bills = billDao, database = db)
            val transactionId = repository.upsert(Transaction(title = "Electricity payment", amountMinor = 20_000,
                type = TransactionType.EXPENSE, status = TransactionStatus.CONFIRMED, category = "Other",
                occurredAt = Instant.fromEpochMilliseconds(0), sourceApp = null, captureId = null, accountId = 1))
            val selectedOccurrence = LocalDate(2026, 3, 15)

            val paymentId = billDao.settle(billId, selectedOccurrence, transactionId, 1)

            assertTrue(paymentId > 0)
            assertEquals(selectedOccurrence, billDao.paymentById(paymentId)!!.toDomain().dueOn)
            assertEquals(0, billDao.byId(billId)!!.settled)
            repository.delete(transactionId)

            assertNull(db.transactionDao().byId(transactionId))
            assertNull(billDao.paymentById(paymentId))
            assertEquals(emptyList(), billDao.paymentsForTransaction(transactionId))
            assertEquals(0, billDao.byId(billId)!!.settled)
            assertEquals("DELETE", db.transactionDao().pendingChanges().single().operation)
        } finally { db.close() }
    }

    @Test
    fun `confirming detected bill preserves existing recurring schedule and payment dates`() = runTest {
        val db = buildDatabase()
        try {
            val dao = db.billDao()
            val startsOn = LocalDate(2026, 1, 15)
            val existingId = dao.save(BillEntity(name = "Electricity", amountMinor = 10_000, category = "Bills", accountId = 1,
                startsOnDay = startsOn.toEpochDays(), repeats = BillRepeat.MONTHLY.name, settled = 0,
                status = TransactionStatus.CONFIRMED.name, detected = false, sourceApp = "com.utility", captureId = null,
                remindedForDay = null, createdAtMillis = 0))
            val paymentId = dao.settle(existingId, startsOn, null, 1)
            val draftId = dao.save(BillEntity(name = "electricity", amountMinor = 12_000, category = "Bills", accountId = 1,
                startsOnDay = LocalDate(2026, 2, 15).toEpochDays(), repeats = BillRepeat.MONTHLY.name, settled = 0,
                status = TransactionStatus.NEEDS_REVIEW.name, detected = true, sourceApp = "com.utility", captureId = null,
                remindedForDay = null, createdAtMillis = 0))

            dao.confirm(draftId)

            val confirmed = dao.byId(existingId)!!
            assertEquals(startsOn.toEpochDays(), confirmed.startsOnDay)
            assertEquals(12_000L, confirmed.amountMinor)
            assertEquals(1, confirmed.settled)
            assertEquals(startsOn, dao.paymentById(paymentId)!!.toDomain().dueOn)
            assertNull(dao.byId(draftId))
        } finally { db.close() }
    }
}
