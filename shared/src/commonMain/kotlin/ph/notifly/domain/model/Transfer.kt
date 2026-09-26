package ph.notifly.domain.model

import kotlin.time.Duration.Companion.minutes

/** Notifications this close together, with a matching amount and opposite directions, are treated as one transfer. */
val TRANSFER_WINDOW = 2.minutes

/**
 * Whether this row is the receiving end of a single-app leg, or null when it is not one leg: a
 * merged transfer (both ends known) or a transfer of unknown direction.
 */
val Transaction.inboundLeg: Boolean?
    get() = when (type) {
        TransactionType.INCOME -> true
        TransactionType.EXPENSE -> false
        TransactionType.TRANSFER -> when {
            fromApp != null && toApp != null -> null
            toApp != null -> true
            fromApp != null -> false
            else -> null
        }
    }

/**
 * True when [this] and [other] look like the two notifications a single transfer between the
 * user's own accounts produces: same amount and currency, opposite [inboundLeg] directions,
 * different source apps, both still awaiting review, and close enough in time.
 */
fun Transaction.isTransferPairWith(other: Transaction): Boolean =
    status == TransactionStatus.NEEDS_REVIEW && other.status == TransactionStatus.NEEDS_REVIEW &&
        amountMinor == other.amountMinor && currency == other.currency &&
        sourceApp != null && other.sourceApp != null && sourceApp != other.sourceApp &&
        inboundLeg != null && other.inboundLeg != null && inboundLeg != other.inboundLeg &&
        (occurredAt - other.occurredAt).absoluteValue <= TRANSFER_WINDOW
