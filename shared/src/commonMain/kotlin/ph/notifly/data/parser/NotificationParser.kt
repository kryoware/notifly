package ph.notifly.data.parser

import ph.notifly.domain.model.TransactionType

/**
 * Rule-based parser for PH wallet and bank notifications.
 *
 * Design rules:
 *  - Never guess an amount. No amount => Unrecognized.
 *  - An amount without a transaction verb is a balance notice, not a transaction.
 *  - Self-transfers are flagged, not silently counted as spending.
 *  - Pre-authorisation holds are flagged: the final amount posts later.
 */
class NotificationParser {

    private val amountRegex = Regex(
        """(?:PHP|Php|php|₱)\s?(\d{1,3}(?:,\d{3})*(?:\.\d{2})?|\d+(?:\.\d{2})?)""",
    )

    private val inbound = listOf("received", "credited", "refund", "deposited", "cash in", "received from")
    private val outbound = listOf("paid", "payment", "debited", "sent", "purchase", "withdrawn", "cash out", "charged")
    private val holdWords = listOf("hold", "pre-auth", "preauth", "authorization hold", "may differ")
    private val balanceWords = listOf("balance is", "available balance", "current balance", "as of")
    private val selfTransferHints = listOf("your own", "to your", "own account", "savings ending", "between your")
    private val genericNameWords = setOf("bank", "app", "mobile", "online", "digital", "pay", "wallet", "savings")

    /**
     * Parses the first matching PHP amount into minor units. Empty text, a missing amount, or
     * no recognized transaction or hold wording returns [ParseOutcome.Unrecognized].
     * [financeApps] are labels of the user's other finance apps; naming one marks the draft as a likely transfer.
     *
     * @throws NumberFormatException if the matched amount's whole-number portion cannot fit in a Long.
     */
    fun parse(body: String, financeApps: Collection<String> = emptyList()): ParseOutcome {
        val text = body.trim()
        if (text.isEmpty()) return ParseOutcome.Unrecognized("Empty notification body.")

        val match = amountRegex.find(text)
            ?: return ParseOutcome.Unrecognized(
                "No amount found. This app's format may not be covered by the current rules yet.",
            )

        val lower = text.lowercase()
        val inWord = inbound.firstOrNull { lower.contains(it) }
        val outWord = outbound.firstOrNull { lower.contains(it) }
        val isHold = holdWords.any { lower.contains(it) }

        if (inWord == null && outWord == null && !isHold) {
            val why = if (balanceWords.any { lower.contains(it) }) {
                "Found an amount but no transaction verb. Reads as a balance notice, so nothing was created."
            } else {
                "Found an amount but could not tell whether money came in or went out."
            }
            return ParseOutcome.Unrecognized(why)
        }

        val mentionedApp = mentionedApp(text, financeApps)
        val isSelfTransfer = mentionedApp != null || selfTransferHints.any { lower.contains(it) }

        // Both directions present is genuinely ambiguous — do not silently pick one.
        val ambiguousDirection = inWord != null && outWord != null

        val type = when {
            isSelfTransfer -> TransactionType.TRANSFER
            inWord != null && outWord == null -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        val merchant = extractMerchant(text)

        val reason = when {
            mentionedApp != null ->
                "Mentions $mentionedApp, another of your finance apps. Probably a transfer — counting it would double your spending."
            isSelfTransfer ->
                "Destination looks like another account of yours. Probably a transfer — counting it would double your spending."
            isHold ->
                "Pre-authorisation hold. The real amount posts later, so this may need editing after it settles."
            ambiguousDirection ->
                "Both inbound and outbound wording appear. Direction needs a human."
            merchant == null ->
                "Amount and direction matched, but no merchant could be read from the text."
            else ->
                "Amount and direction matched cleanly."
        }

        val draft = TransactionDraft(
            amountMinor = toMinorUnits(match.groupValues[1]),
            currency = "PHP",
            type = type,
            merchant = merchant,
            matchedAmount = match.value,
            matchedDirection = inWord ?: outWord ?: holdWords.first { lower.contains(it) },
            amountConfidence = if (isHold) Confidence.LOW else Confidence.HIGH,
            directionConfidence = if (ambiguousDirection || (inWord == null && outWord == null)) {
                Confidence.LOW
            } else {
                Confidence.HIGH
            },
            merchantConfidence = if (merchant == null) Confidence.LOW else Confidence.HIGH,
        )
        return ParseOutcome.Parsed(draft, reason)
    }

    /** "48,000.00" -> 4800000. String maths only; never Double for money. */
    internal fun toMinorUnits(raw: String): Long {
        val cleaned = raw.replace(",", "")
        val parts = cleaned.split(".")
        val whole = parts[0].toLong()
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLong() else 0L
        return whole * 100 + frac
    }

    /** Whole label or any distinctive word of it, so "BDO" in the text matches "BDO Digital". */
    private fun mentionedApp(text: String, financeApps: Collection<String>): String? = financeApps.firstOrNull { label ->
        (label.split(Regex("[^A-Za-z0-9]+")).filter { it.length >= 3 && it.lowercase() !in genericNameWords } + label.trim())
            .any { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
    }

    /** Takes the token run after "to"/"from". Deliberately crude — merchant is low-stakes. */
    private fun extractMerchant(text: String): String? {
        val m = Regex("""\b(?:to|from|by)\s+([A-Z0-9][A-Za-z0-9&'.\- ]{2,40})""").find(text)
        return m?.groupValues?.get(1)
            ?.substringBefore(". ")
            ?.trim()
            ?.trimEnd('.', ',')
            ?.takeIf { it.isNotBlank() }
    }
}
