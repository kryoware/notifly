package ph.notifly.data.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ph.notifly.domain.model.TransactionType
import ph.notifly.data.local.AppDatabase
import ph.notifly.data.local.AllowedAppEntity
import ph.notifly.data.repository.AllowListRepositoryImpl
import ph.notifly.data.repository.CaptureRepositoryImpl
import ph.notifly.domain.source.NotificationContent
import ph.notifly.domain.source.NotificationEvent
import ph.notifly.domain.source.NotificationTransactionSource
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class ModelNotificationParserTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val classifier = NotificationClassifier(
        context.assets.open("notification_model.json").bufferedReader().use { it.readText() },
    )
    private val parser = ModelNotificationParser(context, NotificationParser())

    @Test fun bundledClassifierMatchesPythonAndIgnoresChangingNumbers() {
        val original = classifier.classify("GCash", "You received PHP 1200.00 from Juan. Ref 1234.")
        val changed = classifier.classify("GCash", "You received PHP 98765.43 from Juan. Ref 998877.")
        assertEquals(original, changed)
        assertEquals("income", original.direction)
        assertEquals(null, original.label) // Direction alone never proves account ownership.
        assertEquals("unknown_ownership", original.reason)
        // Reference values from classifier.py using the same exported asset.
        assertEquals(0.8348535949641352, original.confidence, 1e-9)
        assertEquals(0.033231230306498875, original.probabilities.getValue("expense"), 1e-9)
        assertEquals(0.1319151747293658, original.probabilities.getValue("other"), 1e-9)
        assertTrue(classifier.demoOnly)
    }

    @Test fun classifierAddsDirectionWhereRulesHaveNoVerbAndExtractsRawAmount() {
        val result = assertIs<ParseOutcome.Parsed>(parser.parse(
            "GCash", "PHP 800.00 added to your wallet from ACME CORP.",
        ))
        assertEquals(TransactionType.INCOME, result.draft.type)
        assertEquals(80_000L, result.draft.amountMinor)
        assertEquals("ACME CORP", result.draft.merchant)
        assertEquals(true, result.draft.inbound)
        assertTrue(result.draft.matchedDirection.startsWith("On-device model"))
        val changed = assertIs<ParseOutcome.Parsed>(parser.parse(
            "GCash", "PHP 98765.43 added to your wallet from ACME CORP.",
        ))
        assertEquals(9_876_543L, changed.draft.amountMinor)
    }

    @Test fun otherNotificationsAreUnrecognizedAndUncertainRulesNeedReview() {
        assertIs<ParseOutcome.Unrecognized>(parser.parse("Messages", "Your OTP is 123456. Do not share it."))
        assertIs<ParseOutcome.Unrecognized>(parser.parse("ShopBack", "Save PHP 500.00 today. Shop now and get cashback."))
        assertIs<ParseOutcome.Unrecognized>(parser.parse("GCash", "Payment of PHP 100.00 to SHOP failed."))
        assertIs<ParseOutcome.Unrecognized>(parser.parse("GCash", "x".repeat(4097)))
        assertIs<ParseOutcome.Unrecognized>(parser.parse("GCash", ""))
        val uncertain = assertIs<ParseOutcome.Parsed>(parser.parse("Grab", "You paid ₱569.00 to GRAB."))
        assertEquals(56_900L, uncertain.draft.amountMinor)
        assertEquals(Confidence.LOW, uncertain.draft.directionConfidence)
        assertTrue(uncertain.draft.needsReview)
        assertTrue(uncertain.reason.contains("Model confidence is low"))
    }

    @Test fun likelySelfTransfersStillNeedOwnershipReview() {
        val result = assertIs<ParseOutcome.Parsed>(parser.parse(
            "GCash", "You received PHP 1200.00 from Juan. Ref 1234.", listOf("Juan Bank"),
        ))
        assertEquals(TransactionType.TRANSFER, result.draft.type)
        assertEquals(true, result.draft.inbound)
        assertTrue(result.draft.needsReview)
    }

    @Test fun captureUsesModelOnlyForAllowedAppsAndKeepsLedgerInReview() = runTest {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        try {
            // Missing installed packages exercise the normal package-name source fallback.
            db.allowedAppDao().upsert(AllowedAppEntity("GCash", "GCash", "Wallet", listening = true, finance = true))
            val ledger = ph.notifly.data.repository.LedgerRepositoryImpl(db.ledgerDao(), ph.notifly.data.local.appPreferences(context))
            ledger.saveAccount(ph.notifly.domain.model.Account(1, "GCash", ph.notifly.domain.model.AccountType.WALLET, linkedApps = setOf("GCash")))
            val source = NotificationTransactionSource(context, CaptureRepositoryImpl(db.rawCaptureDao()),
                AllowListRepositoryImpl(db.allowedAppDao()), parser, ledger)
            source.capture(NotificationEvent("blocked", "blocked.app", 0) { error("Blocked app content must not be read") })
            assertTrue(db.rawCaptureDao().observeLog(null).first().isEmpty())
            val body = "PHP 800.00 added to your wallet from ACME CORP."
            val event = NotificationEvent("model-income", "GCash", 0) { NotificationContent("", body) }
            source.capture(event)
            source.capture(event)
            val row = db.transactionDao().observeAll().first().single()
            assertEquals("INCOME", row.type)
            assertEquals("NEEDS_REVIEW", row.status)
            assertEquals(80_000L, row.amountMinor)
            assertEquals(0L, db.transactionDao().observeConfirmedNetMinor().first())
            assertNull(db.rawCaptureDao().observeLog(null).first().single().body)
            source.capture(NotificationEvent("otp", "GCash", 0) {
                NotificationContent("", "Your OTP is 123456. Do not share it.")
            })
            assertEquals(1, db.transactionDao().observeAll().first().size)
            assertTrue(db.rawCaptureDao().observeLog(null).first().any { it.result == "UNRECOGNIZED" })
        } finally { db.close() }
    }
}
