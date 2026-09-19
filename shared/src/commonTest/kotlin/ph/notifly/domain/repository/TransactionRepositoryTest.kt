package ph.notifly.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

class TransactionRepositoryTest {

    private val repo = FakeTransactionRepository()

    private fun transaction(
        title: String = "Test",
        amountMinor: Long = 10_000L,
        type: TransactionType = TransactionType.EXPENSE,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        title = title,
        amountMinor = amountMinor,
        type = type,
        status = status,
        category = "Misc",
        occurredAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        sourceApp = null,
        captureId = null,
    )

    @Test
    fun `observeConfirmedNetMinor excludes NEEDS_REVIEW`() = runTest {
        repo.upsert(transaction(title = "Confirmed income", amountMinor = 50_000L, type = TransactionType.INCOME, status = TransactionStatus.CONFIRMED))
        repo.upsert(transaction(title = "Confirmed expense", amountMinor = 10_000L, type = TransactionType.EXPENSE, status = TransactionStatus.CONFIRMED))
        repo.upsert(transaction(title = "Pending income", amountMinor = 999_999L, type = TransactionType.INCOME, status = TransactionStatus.NEEDS_REVIEW))
        repo.upsert(transaction(title = "Pending expense", amountMinor = 123_456L, type = TransactionType.EXPENSE, status = TransactionStatus.NEEDS_REVIEW))

        val net = repo.observeConfirmedNetMinor().first()

        // Only the two CONFIRMED entries: 50000 - 10000 = 40000
        assertEquals(40_000L, net)
    }

    @Test
    fun `observeConfirmedNetMinor is zero when no confirmed`() = runTest {
        repo.upsert(transaction(status = TransactionStatus.NEEDS_REVIEW))
        assertEquals(0L, repo.observeConfirmedNetMinor().first())
    }

    @Test
    fun `transfers do not affect confirmed net`() = runTest {
        repo.upsert(transaction(amountMinor = 1_000L, type = TransactionType.TRANSFER, status = TransactionStatus.CONFIRMED))
        assertEquals(0L, repo.observeConfirmedNetMinor().first())
    }

    @Test
    fun `upsert and byId round-trip`() = runTest {
        val txn = transaction(title = "Payroll", amountMinor = 4_800_000L, type = TransactionType.INCOME)
        val id = repo.upsert(txn)
        val stored = repo.byId(id)
        assertEquals(txn.copy(id = id), stored)
    }

    @Test
    fun `delete removes transaction`() = runTest {
        val id = repo.upsert(transaction())
        repo.delete(id)
        assertEquals(null, repo.byId(id))
    }

    @Test
    fun `observeByStatus filters correctly`() = runTest {
        repo.upsert(transaction(title = "A", status = TransactionStatus.CONFIRMED))
        repo.upsert(transaction(title = "B", status = TransactionStatus.NEEDS_REVIEW))
        repo.upsert(transaction(title = "C", status = TransactionStatus.CONFIRMED))

        val confirmed = repo.observeByStatus(TransactionStatus.CONFIRMED).first()
        assertEquals(2, confirmed.size)
        assertEquals(listOf("A", "C"), confirmed.map { it.title }.sorted())
    }
}
