package ph.notifly.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val BASE = Instant.fromEpochMilliseconds(1_000_000_000_000)

private fun draft(
    type: TransactionType,
    sourceApp: String?,
    amountMinor: Long = 50000,
    occurredAt: Instant = BASE,
    status: TransactionStatus = TransactionStatus.NEEDS_REVIEW,
) = Transaction(
    title = "t", amountMinor = amountMinor, type = type, status = status,
    category = "Other", occurredAt = occurredAt, sourceApp = sourceApp, captureId = null,
)

class TransferTest {
    @Test fun matchesOppositeDirectionsSameAmountDifferentAppsWithinWindow() {
        val a = draft(TransactionType.EXPENSE, "com.maya")
        val b = draft(TransactionType.INCOME, "com.maribank", occurredAt = BASE + 90.seconds)
        assertTrue(a.isTransferPairWith(b))
        assertTrue(b.isTransferPairWith(a))
    }
    @Test fun rejectsSameDirection() {
        val a = draft(TransactionType.EXPENSE, "com.maya")
        val b = draft(TransactionType.EXPENSE, "com.maribank", occurredAt = BASE + 30.seconds)
        assertFalse(a.isTransferPairWith(b))
    }
    @Test fun rejectsOutsideWindow() {
        val a = draft(TransactionType.EXPENSE, "com.maya")
        val b = draft(TransactionType.INCOME, "com.maribank", occurredAt = BASE + 2.minutes + 1.seconds)
        assertFalse(a.isTransferPairWith(b))
    }
    @Test fun rejectsSameApp() {
        val a = draft(TransactionType.EXPENSE, "com.maya")
        val b = draft(TransactionType.INCOME, "com.maya", occurredAt = BASE + 30.seconds)
        assertFalse(a.isTransferPairWith(b))
    }
    @Test fun rejectsDifferentAmount() {
        val a = draft(TransactionType.EXPENSE, "com.maya", amountMinor = 50000)
        val b = draft(TransactionType.INCOME, "com.maribank", amountMinor = 50001, occurredAt = BASE + 30.seconds)
        assertFalse(a.isTransferPairWith(b))
    }
    @Test fun rejectsWhenEitherAlreadyConfirmed() {
        val a = draft(TransactionType.EXPENSE, "com.maya", status = TransactionStatus.CONFIRMED)
        val b = draft(TransactionType.INCOME, "com.maribank", occurredAt = BASE + 30.seconds)
        assertFalse(a.isTransferPairWith(b))
    }
}
