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
}
