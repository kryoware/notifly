package ph.notifly.data.local

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ph.notifly.data.repository.CaptureRepositoryImpl
import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class CapturePersistenceTest {
    @Test fun revokingRetentionHidesAndErasesBodiesAndPreventsNewRetention() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File.createTempFile("retain", ".preferences_pb").also { it.delete() }
        val storeJob = kotlinx.coroutines.SupervisorJob()
        val storeScope = kotlinx.coroutines.CoroutineScope(storeJob + kotlinx.coroutines.Dispatchers.IO)
        val preferences = AppPreferences(androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = storeScope) { file })
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        try {
            val repository = CaptureRepositoryImpl(db.rawCaptureDao(), preferences = preferences)
            val capture = RawCapture(sourceApp = "wallet", capturedAt = Clock.System.now(), body = "private",
                result = CaptureResult.UNRECOGNIZED, reason = "Not recognised")
            repository.record(capture)
            assertNull(repository.observeLog().first().single().body)
            repository.redactBodies()
            repository.record(capture)
            assertTrue(db.rawCaptureDao().observeLog(null).first().all { it.body == null })
        } finally { db.close(); storeJob.cancel(); storeJob.join(); file.delete() }
    }
    @Test fun duplicateDeliveryIsAtomicAndAlwaysNeedsReview() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver()).build()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val capture = RawCapture(sourceApp = "wallet", capturedAt = Clock.System.now(), body = "private text",
                result = CaptureResult.PARSED, reason = "Parsed", fingerprint = "same-notification-and-content")
            val transaction = Transaction(title = "Merchant", amountMinor = 1250, type = TransactionType.EXPENSE,
                status = TransactionStatus.NEEDS_REVIEW, category = "Other", occurredAt = Clock.System.now(), sourceApp = "wallet", captureId = null)
            val id = captures.recordParsed(capture, transaction)
            assertTrue(id > 0)
            assertEquals(-1L, captures.recordParsed(capture, transaction))
            assertEquals(1, db.transactionDao().observeAll().first().size)
            assertEquals(id, db.transactionDao().observeAll().first().single().captureId)
            assertEquals(0L, db.transactionDao().observeConfirmedNetMinor().first())
            assertNull(captures.observeLog().first().single().body)
            assertFailsWith<IllegalArgumentException> {
                captures.recordParsed(capture.copy(fingerprint = "new"), transaction.copy(status = TransactionStatus.CONFIRMED))
            }
            assertEquals(1, captures.observeLog().first().size)
            captures.clearLog()
            assertEquals(-1L, captures.recordParsed(capture, transaction))
            assertEquals(1, db.transactionDao().observeAll().first().size)
            db.transactionDao().delete(db.transactionDao().observeAll().first().single().id)
            assertEquals(-1L, captures.recordParsed(capture, transaction))
            assertEquals(0, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }

    private fun leg(app: String, pkg: String, type: TransactionType, amountMinor: Long, occurredAt: kotlin.time.Instant, fingerprint: String) =
        RawCapture(sourceApp = app, capturedAt = occurredAt, body = "body", result = CaptureResult.PARSED, reason = "Parsed", fingerprint = fingerprint) to
            Transaction(title = "Payment", amountMinor = amountMinor, type = type, status = TransactionStatus.NEEDS_REVIEW,
                category = "Other", occurredAt = occurredAt, sourceApp = pkg, captureId = null)

    private suspend fun transferDb(vararg finance: String = arrayOf("com.maya", "com.maribank")): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver()).build()
        finance.forEach { db.allowedAppDao().upsert(AllowedAppEntity(it, it, "Bank", listening = true, finance = true)) }
        return db
    }

    @Test fun transferPairMergesIntoOneReviewRow() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (outCapture, outTx) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "leg-out")
            val (inCapture, inTx) = leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 30.seconds, "leg-in")
            captures.recordParsed(outCapture, outTx)
            captures.recordParsed(inCapture, inTx)
            val rows = db.transactionDao().observeAll().first()
            assertEquals(1, rows.size)
            assertEquals("TRANSFER", rows.single().type)
            assertEquals("NEEDS_REVIEW", rows.single().status)
            assertEquals("Maya → MariBank", rows.single().title)
            assertEquals("com.maya" to "com.maribank", rows.single().fromApp to rows.single().toApp)
            assertEquals(2, captures.observeLog().first().size)
            assertEquals(0L, db.transactionDao().observeConfirmedNetMinor().first())
        } finally { db.close() }
    }
    @Test fun mergedTransferIsNotMatchedAgain() = runTest {
        val db = transferDb("com.maya", "com.maribank", "com.gcash")
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            listOf(
                leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "third-1"),
                leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 30.seconds, "third-2"),
                leg("GCash", "com.gcash", TransactionType.INCOME, 50000, now + 60.seconds, "third-3"),
            ).forEach { (c, t) -> captures.recordParsed(c, t) }
            val rows = db.transactionDao().observeAll().first()
            assertEquals(2, rows.size)
            assertEquals(1, rows.count { it.title == "Maya → MariBank" })
        } finally { db.close() }
    }
    @Test fun flaggedTransferLegKeepsItsDirection() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("MariBank", "com.maribank", TransactionType.EXPENSE, 50000, now, "flagged-1")
            val (c2, t2) = leg("Maya", "com.maya", TransactionType.TRANSFER, 50000, now + 30.seconds, "flagged-2")
            captures.recordParsed(c1, t1)
            captures.recordParsed(c2, t2.copy(fromApp = "com.maya"))
            assertEquals(2, db.transactionDao().observeAll().first().size)
            val (c3, t3) = leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 45.seconds, "flagged-3")
            captures.recordParsed(c3, t3)
            assertEquals("Maya → MariBank", db.transactionDao().observeAll().first().single { it.type == "TRANSFER" }.title)
        } finally { db.close() }
    }
    @Test fun sameDirectionDoesNotMerge() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "same-dir-1")
            val (c2, t2) = leg("MariBank", "com.maribank", TransactionType.EXPENSE, 50000, now + 30.seconds, "same-dir-2")
            captures.recordParsed(c1, t1)
            captures.recordParsed(c2, t2)
            assertEquals(2, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }
    @Test fun outsideWindowDoesNotMerge() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "window-1")
            val (c2, t2) = leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 3.minutes, "window-2")
            captures.recordParsed(c1, t1)
            captures.recordParsed(c2, t2)
            assertEquals(2, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }
    @Test fun sameAppDoesNotMerge() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "same-app-1")
            val (c2, t2) = leg("Maya", "com.maya", TransactionType.INCOME, 50000, now + 30.seconds, "same-app-2")
            captures.recordParsed(c1, t1)
            captures.recordParsed(c2, t2)
            assertEquals(2, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }
    @Test fun confirmedCandidateDoesNotMerge() = runTest {
        val db = transferDb()
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "confirmed-1")
            captures.recordParsed(c1, t1)
            val stored = db.transactionDao().observeAll().first().single()
            db.transactionDao().upsert(stored.copy(status = "CONFIRMED"))
            val (c2, t2) = leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 30.seconds, "confirmed-2")
            captures.recordParsed(c2, t2)
            assertEquals(2, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }
    @Test fun nonFinanceAppDoesNotMerge() = runTest {
        val db = transferDb("com.maya")
        try {
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val now = Clock.System.now()
            val (c1, t1) = leg("Maya", "com.maya", TransactionType.EXPENSE, 50000, now, "non-finance-1")
            val (c2, t2) = leg("MariBank", "com.maribank", TransactionType.INCOME, 50000, now + 30.seconds, "non-finance-2")
            captures.recordParsed(c1, t1)
            captures.recordParsed(c2, t2)
            assertEquals(2, db.transactionDao().observeAll().first().size)
        } finally { db.close() }
    }
}
