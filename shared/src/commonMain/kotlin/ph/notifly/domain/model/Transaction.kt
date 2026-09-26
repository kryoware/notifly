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
    val sourceApp: String?,         // null for manual entries
    val captureId: Long?,           // links back to the RawCapture that produced it
    val note: String = "",
    // TRANSFER only: packages the money left and reached. One side is set per captured leg, both once merged.
    val fromApp: String? = null,
    val toApp: String? = null,
)
