package ph.notifly.domain.model

import kotlin.time.Instant

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * CONFIRMED    — the user has seen and accepted it. Counts toward the headline balance.
 * NEEDS_REVIEW — parsed but unconfirmed. Shown separately; excluded from the headline balance.
 * UNRECOGNIZED — never becomes a Transaction. See [RawCapture] instead.
 */
enum class TransactionStatus { CONFIRMED, NEEDS_REVIEW }

data class Transaction(
    val id: Long = 0,
    val title: String,
    val amountMinor: Long,          // centavos. Never Double for money.
    val currency: String = "PHP",
    val type: TransactionType,
    val status: TransactionStatus,
    val category: String,
    val occurredAt: Instant,
    val createdAt: Instant = occurredAt,
    val sourceApp: String?,         // optional package; manual entries can also be attributed to an app
    val captureId: Long?,           // links back to the RawCapture that produced it
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    // Notification provenance only. accountId and toAccountId determine money movements.
    val fromApp: String? = null,
    val toApp: String? = null,
)
