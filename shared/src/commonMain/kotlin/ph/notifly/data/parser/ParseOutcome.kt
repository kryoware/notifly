package ph.notifly.data.parser

import ph.notifly.domain.model.TransactionType

/** Per-field confidence. Amount and direction corrupt the ledger if wrong; merchant does not. */
enum class Confidence { HIGH, LOW }

data class TransactionDraft(
    val amountMinor: Long,
    val currency: String,
    val type: TransactionType,
    val merchant: String?,
    val matchedAmount: String,
    val matchedDirection: String,
    val amountConfidence: Confidence,
    val directionConfidence: Confidence,
    val merchantConfidence: Confidence,
    /** Whether wording says money came in, or null when unclear. For a TRANSFER this is which end the app is. */
    val inbound: Boolean? = null,
) {
    /** Anything less than fully confident goes to the review queue. */
    val needsReview: Boolean
        get() = amountConfidence == Confidence.LOW ||
                directionConfidence == Confidence.LOW ||
                type == TransactionType.TRANSFER
}

sealed interface ParseOutcome {
    data class Parsed(val draft: TransactionDraft, val reason: String) : ParseOutcome
    /** Deliberately not a transaction. Never invent numbers to avoid this branch. */
    data class Unrecognized(val reason: String) : ParseOutcome
}
