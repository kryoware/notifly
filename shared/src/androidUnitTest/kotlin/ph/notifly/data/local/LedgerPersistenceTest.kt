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
import ph.notifly.data.repository.TransactionRepositoryImpl
import ph.notifly.domain.model.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class LedgerPersistenceTest {
    private fun database() = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
        .setDriver(AndroidSQLiteDriver()).build()
    private fun transaction(account: Long = 1, type: TransactionType = TransactionType.EXPENSE, to: Long? = null) =
        Transaction(title = "Test transaction", amountMinor = 100, type = type,
            status = TransactionStatus.CONFIRMED, category = "Other", occurredAt = Clock.System.now(),
            sourceApp = null, captureId = null, accountId = account, toAccountId = to)

    @Test fun validatesReferencesAndRenamesWithoutLosingBudgetsOrHistory() = runTest {
        val db = database()
        try {
            db.seedTestAccounts()
            val dao = db.ledgerDao()
            val repository = TransactionRepositoryImpl(db.transactionDao())
            assertFailsWith<IllegalArgumentException> { repository.upsert(transaction(999)) }
            assertFailsWith<IllegalArgumentException> { repository.upsert(transaction(type = TransactionType.TRANSFER)) }
            assertFailsWith<IllegalArgumentException> { repository.upsert(transaction(type = TransactionType.TRANSFER, to = 1)) }
            val id = repository.upsert(transaction())
            val category = dao.categoryById(repository.byId(id)!!.categoryId!!)!!
            dao.saveCategory(category.toDomain().copy(budgetMinor = 5000))
            // Other is protected; custom categories can be renamed or archived.
            val customId = dao.saveCategory(Category(name = "Coffee", type = TransactionType.EXPENSE, budgetMinor = 500))
            val coffeeId = repository.upsert(transaction().copy(category = "Coffee", categoryId = customId))
            dao.saveCategory(dao.categoryById(customId)!!.toDomain().copy(name = "Cafes"))
            assertEquals("Cafes", repository.byId(coffeeId)!!.category)
            assertEquals(customId, repository.byId(coffeeId)!!.categoryId)
            assertEquals(500L, dao.categoryById(customId)!!.budgetMinor)
            assertTrue(dao.pendingChanges().any { it.transactionId == coffeeId })
            dao.saveCategory(dao.categoryById(customId)!!.toDomain().copy(archived = true))
            assertFailsWith<IllegalArgumentException> { repository.upsert(transaction().copy(categoryId = customId)) }
            repository.upsert(repository.byId(coffeeId)!!.copy(amountMinor = 200))
            assertFailsWith<IllegalArgumentException> { dao.saveAccount(Account(1, "Maya", AccountType.CARD)) }
            assertFailsWith<IllegalArgumentException> { dao.deleteAccount(1) }
            dao.saveAccount(dao.accountById(1)!!.toDomain(dao.links()).copy(archived = true))
            assertFailsWith<IllegalArgumentException> { repository.upsert(transaction()) }
            assertEquals(id, repository.upsert(repository.byId(id)!!.copy(amountMinor = 300)))
        } finally { db.close() }
    }

    @Test fun unresolvedDraftsSurviveRawExpiryAndConfirmExactlyOnce() = runTest {
        val db = database()
        try {
            db.seedTestAccounts()
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val at = Clock.System.now() - 25.hours
            val capture = RawCapture(sourceApp = "Bank", capturedAt = at, body = "private notification",
                result = CaptureResult.NEEDS_REVIEW, reason = "Choose account", fingerprint = "draft-fingerprint")
            val draft = CapturedDraft(title = "Purchase", amountMinor = 1234, type = TransactionType.EXPENSE,
                inbound = false, occurredAt = at, sourceApp = "bank", accountHint = "1234")
            assertTrue(captures.recordDraft(capture, draft) > 0)
            assertEquals(-1L, captures.recordDraft(capture, draft))
            assertTrue(db.transactionDao().observeAll().first().isEmpty())
            assertNull(captures.observeLog().first().single().body)
            captures.purgeExpired()
            assertTrue(captures.observeLog().first().isEmpty())
            val saved = db.ledgerDao().drafts().single()
            assertEquals(1234L, saved.amountMinor)
            assertEquals("1234", saved.accountHint)
            val transactionId = db.ledgerDao().confirmDraft(saved.id, transaction().copy(amountMinor = saved.amountMinor).toEntity())
            assertTrue(db.ledgerDao().drafts().isEmpty())
            assertEquals("CONFIRMED", db.transactionDao().byId(transactionId)!!.status)
            assertFailsWith<IllegalStateException> { db.ledgerDao().confirmDraft(saved.id, transaction().toEntity()) }
        } finally { db.close() }
    }

    @Test fun preferenceImportIsIdempotentAndPreservesReconciliationCutoff() = runTest {
        val db = database()
        try {
            val at = Instant.fromEpochMilliseconds(12345)
            val balances = mapOf("wallet" to ManualBalance(90000, at))
            val budgets = mapOf("Food" to 3000L)
            db.ledgerDao().importPreferences(balances, budgets)
            val account = db.ledgerDao().accounts().single()
            assertEquals(90000L, account.balanceMinor)
            assertEquals(12345L, account.balanceAsOfMillis)
            db.ledgerDao().saveAccount(account.toDomain(db.ledgerDao().links()).copy(balanceMinor = 50000, linkedApps = emptySet()))
            db.ledgerDao().importPreferences(balances, budgets)
            assertEquals(50000L, db.ledgerDao().accounts().single().balanceMinor)
            assertEquals(3000L, db.ledgerDao().categories().single { it.name == "Food" }.budgetMinor)
            assertEquals(2, db.ledgerDao().categories().count { it.name == "Other" })
        } finally { db.close() }
    }

    @Test fun sameAppTransferPairsByDistinctAccountsAndAmbiguousPairsRemainSeparate() = runTest {
        val db = database()
        try {
            db.seedTestAccounts()
            val captures = CaptureRepositoryImpl(db.rawCaptureDao())
            val at = Clock.System.now()
            fun capture(key: String) = RawCapture(sourceApp = "Bank", capturedAt = at, body = null,
                result = CaptureResult.NEEDS_REVIEW, reason = "Parsed", fingerprint = key)
            fun draft(account: Long, inbound: Boolean) = CapturedDraft(title = "Transfer", amountMinor = 1000,
                type = TransactionType.TRANSFER, inbound = inbound, occurredAt = at, sourceApp = "same.bank",
                accountId = account)
            captures.recordDraft(capture("out"), draft(1, false))
            assertEquals(1, db.ledgerDao().drafts().size)
            captures.recordDraft(capture("in"), draft(2, true))
            val transfer = db.transactionDao().observeAll().first().single()
            assertEquals(1L, transfer.accountId)
            assertEquals(2L, transfer.toAccountId)
            assertEquals("NEEDS_REVIEW", transfer.status)
            assertTrue(db.ledgerDao().drafts().isEmpty())
            assertEquals(0, db.transactionDao().pendingChanges().size)
        } finally { db.close() }
    }
}
