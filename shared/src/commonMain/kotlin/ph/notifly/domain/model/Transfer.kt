package ph.notifly.domain.model

import kotlin.time.Duration.Companion.minutes

/** Notifications this close together, with a matching amount and opposite directions, are treated as one transfer. */
val TRANSFER_WINDOW = 2.minutes

/**
 * True when [this] and [other] look like the two notifications a single transfer between the
 * user's own accounts produces: same amount and currency, opposite directions (or a leg the parser
 * already flagged as a transfer), different source apps, both still awaiting review, and close
 * enough in time.
 */
fun Transaction.isTransferPairWith(other: Transaction): Boolean =
    status == TransactionStatus.NEEDS_REVIEW && other.status == TransactionStatus.NEEDS_REVIEW &&
        amountMinor == other.amountMinor && currency == other.currency &&
        sourceApp != null && other.sourceApp != null && sourceApp != other.sourceApp &&
        setOf(type, other.type).let { it == setOf(TransactionType.INCOME, TransactionType.EXPENSE) || TransactionType.TRANSFER in it } &&
        (occurredAt - other.occurredAt).absoluteValue <= TRANSFER_WINDOW
