package ph.notifly.data.parser

import ph.notifly.domain.model.TransactionType

/**
 * Rule-based parser for PH wallet and bank notifications.
 *
 * Design rules:
 *  - Never guess an amount. No amount => Unrecognized.
 *  - Direction needs transaction wording or a confident on-device prediction.
 *  - Self-transfers are flagged, not silently counted as spending.
 *  - Pre-authorisation holds are flagged: the final amount posts later.
 */
class NotificationParser {

    private val selfTransferHints = listOf("your own", "to your account", "own account", "savings ending", "between your")
    private val genericNameWords = setOf("bank", "app", "mobile", "online", "digital", "pay", "wallet", "savings")

    /**
     * Parses a PHP amount with nearby whole-word evidence into minor units. Empty text, a missing amount, or
     * no recognized direction/hold wording or model hint returns [ParseOutcome.Unrecognized].
     * [financeApps] are labels of the user's other finance apps; one named as the counterparty marks a likely transfer.
     *
     * [directionHint] is an optional confident on-device prediction; it never proves account ownership.
     */
    fun parse(
        body: String,
        financeApps: Collection<String> = emptyList(),
        directionHint: TransactionType? = null,
    ): ParseOutcome {
        require(directionHint != TransactionType.TRANSFER) { "Direction cannot establish account ownership" }
        val text = body.trim()
        if (text.isEmpty()) return ParseOutcome.Unrecognized("Empty notification body.")
        if (incomplete.containsMatchIn(text)) return ParseOutcome.Unrecognized("Transaction is incomplete or unsuccessful.")

        val match = amountRegex.find(text) ?: gcashAmountRegex.find(text)
            ?: return ParseOutcome.Unrecognized(
                "No amount found. This app's format may not be covered by the current rules yet.",
            )

        val lower = text.lowercase()
        val inWord = inbound.firstOrNull { lower.contains(it) }
        // "Payment" is a noun in incoming payments; actual outbound verbs still conflict.
        val outWord = outbound.firstOrNull { lower.contains(it) && (it != "payment" || inWord == null) }
        val isHold = holdWords.any { lower.contains(it) }

        if (inWord == null && outWord == null && !isHold &&
            (directionHint == null || balanceWords.any { lower.contains(it) })) {
            val why = if (balanceWords.any { lower.contains(it) }) {
                "Found an amount but no transaction verb. Reads as a balance notice, so nothing was created."
            } else {
                "Found an amount but could not tell whether money came in or went out."
            }
            return ParseOutcome.Unrecognized(why)
        }

        val merchant = extractMerchant(local, hint ?: if (inWord != null && outWord == null) TransactionType.INCOME else null)
        val mentionedApp = merchant?.let { mentionedApp(it, financeApps) }
        val isSelfTransfer = mentionedApp != null || context.nearest(selfTransferHints) != null

        // Both directions present is genuinely ambiguous — do not silently pick one.
        val ambiguousDirection = inWord != null && outWord != null

        val type = when {
            isSelfTransfer -> TransactionType.TRANSFER
            documentExpense -> TransactionType.EXPENSE
            hint != null -> hint
            inWord != null && outWord == null -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        val reason = when {
            mentionedApp != null ->
                "Mentions $mentionedApp, another of your finance apps. Probably a transfer — counting it would double your spending."
            isSelfTransfer ->
                "Destination looks like another account of yours. Probably a transfer — counting it would double your spending."
            isHold ->
                "Pre-authorisation hold. The real amount posts later, so this may need editing after it settles."
            ambiguousDirection ->
                "Both inbound and outbound wording appear. Direction needs a human."
            document != null ->
                "Receipt or invoice needs review; check whether payment has actually settled."
            distant ->
                "Transaction wording is farther from the amount; direction needs review."
            hint != null ->
                "Direction suggested by the on-device model. Check the amount and account ownership."
            merchant == null ->
                "Amount and direction matched, but no merchant could be read from the text."
            else ->
                "Amount and direction matched cleanly."
        }

        val amountMinor = runCatching { toMinorUnits(match.groupValues[1] + match.groupValues[2].trim()) }.getOrNull()
            ?.takeIf { it > 0 }
            ?: return ParseOutcome.Unrecognized("Amount is outside the supported range.")
        val modelDisagrees = hint != null && (
            (hint == TransactionType.INCOME && outWord != null) ||
                (hint == TransactionType.EXPENSE && inWord != null)
            )
        val draft = TransactionDraft(
            amountMinor = amountMinor,
            currency = "PHP",
            type = type,
            merchant = merchant,
            matchedAmount = match.value,
            matchedDirection = hint?.let { "On-device model: ${it.name.lowercase()}" }
                ?: evidence.word,
            amountConfidence = if (isHold || usable.map { it.match.groupValues[1] + it.match.groupValues[2] }.distinct().size > 1) Confidence.LOW else Confidence.HIGH,
            directionConfidence = if (ambiguousDirection || modelDisagrees || distant || document != null || (hint == null && inWord == null && outWord == null)) {
                Confidence.LOW
            } else {
                Confidence.HIGH
            },
            merchantConfidence = if (merchant == null) Confidence.LOW else Confidence.HIGH,
            inbound = if (ambiguousDirection || modelDisagrees) null
                else hint?.let { it == TransactionType.INCOME } ?: inWord?.let { true } ?: outWord?.let { false },
        )
        return ParseOutcome.Parsed(draft, reason)
    }

    /**
     * Converts the first recognized amount starting at or after character offset [from] to minor units.
     * Returns null if no amount matches or conversion fails, including overflow; does not try later matches.
     * Does not filter currency or transaction context. Shared with [BillReminderParser].
     */
    internal fun firstAmountMinor(text: String, from: Int = 0): Long? =
        AmountContexts.find(text).firstOrNull { it.match.range.first >= from && supportedCurrency(text, it.match) }?.match?.let { match ->
            runCatching { toMinorUnits(match.groupValues[1] + match.groupValues.getOrNull(2).orEmpty()) }.getOrNull()
        }

    private fun supportedCurrency(text: String, match: MatchResult): Boolean {
        if (Regex("(?i)^(?:USD|EUR|GBP|\\$|€|£)").containsMatchIn(match.value)) return false
        val before = text.substring(0, match.range.first)
        val after = text.substring(match.range.last + 1)
        return !Regex("^\\s*(?:USD|EUR|GBP)\\b", RegexOption.IGNORE_CASE).containsMatchIn(after) &&
            !Regex("(?:USD|EUR|GBP)\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(before)
    }

    /** "48,000.00" -> 4800000. String maths only; never Double for money. */
    internal fun toMinorUnits(raw: String): Long {
        val thousands = raw.trim().endsWith("k", ignoreCase = true)
        val cleaned = raw.trim().removeSuffix("k").removeSuffix("K").replace(",", "")
        val parts = cleaned.split(".")
        val whole = parts[0].toLong()
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLong() else 0L
        require(whole >= 0 && whole <= (Long.MAX_VALUE - frac) / 100) { "Amount exceeds supported range" }
        val minor = whole * 100 + frac
        require(!thousands || minor <= Long.MAX_VALUE / 1000) { "Amount exceeds supported range" }
        return if (thousands) minor * 1000 else minor
    }

    /**
     * Whole label or a distinctive word leading the counterparty, optionally after possessives.
     * "My BDO account" matches "BDO Digital"
     * but "JOLLIBEE at BDO Mall" does not.
     */
    private fun mentionedApp(counterparty: String, financeApps: Collection<String>): String? = financeApps.firstOrNull { label ->
        (label.split(Regex("[^A-Za-z0-9]+")).filter { it.length >= 3 && it.lowercase() !in genericNameWords } + label.trim())
            .any { Regex("""^(?:(?:my|your|own)\s+)*${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(counterparty) }
    }

    /** Takes the token run after "to"/"from". Deliberately crude — merchant is low-stakes. */
    private fun extractMerchant(text: String, direction: TransactionType?): String? {
        val namePattern = """\s+([A-Z0-9][A-Za-z0-9&'.\- ]{2,40})"""
        val prefix = when (direction) {
            TransactionType.INCOME -> "from"
            TransactionType.EXPENSE -> "(?:to|at)"
            else -> null
        }
        val m = prefix?.let { Regex("""\b$it$namePattern""").find(text) }
            ?: Regex("""\b(?:to|from|by|at)$namePattern""").find(text)
        return m?.groupValues?.get(1)
            ?.substringBefore(". ")
            ?.trim()
            ?.trimEnd('.', ',')
            ?.takeIf { it.isNotBlank() }
    }
}
