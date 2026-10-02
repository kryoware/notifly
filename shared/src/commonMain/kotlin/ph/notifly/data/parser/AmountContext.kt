package ph.notifly.data.parser

/** English evidence must be whole words, <10 words and <=2 physical lines from money. */
internal object AmountContexts {
    private const val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val money = Regex("(?i)(?<![a-z0-9_])(?:PHP|USD|EUR|GBP|₱|\\$|€|£|P(?=\\s*[0-9]))\\s*($number)(\\s*k)?(?![a-z0-9_]|[.,][0-9])")
    private val bare = Regex("(?i)\\byou (?:have )?(?:paid|received|sent)\\s+($number)(\\s*k)?\\s+(?:of\\s+)?GCash\\b")
    private val compact = Regex("(?i)(?<![a-z0-9_.,])($number)(k)(?![a-z0-9_]|[.,][0-9])")
    private val tokens = Regex("[^\\t-\\r \\u001c-\\u001f\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]+")
    val inbound = listOf("received", "credited", "refund", "refunded", "deposited", "cash in", "cashback", "salary", "payroll", "added", "payout", "reimbursement", "incoming", "client paid")
    val outbound = listOf("paid", "payment", "debited", "sent", "purchase", "purchased", "withdrawn", "withdrawal", "cash out", "charged", "transferred", "spent", "repayment", "approved transaction")
    val documents = listOf("receipt", "invoice")
    val holds = listOf("hold", "pre-auth", "preauth", "authorization hold", "may differ")
    private val balances = listOf("balance is", "available balance", "current balance", "new balance", "balance:", "as of")
    private val noise = listOf("price change", "price alert", "price drop", "tracked flight", "tracked price", "prices updated", "offer", "save", "discount", "estimate", "budget", "otp", "verification code", "received this email", "mailing list", "newsletter")
    private val incomplete = listOf("failed", "declined", "unsuccessful", "pending", "cancelled", "canceled")

    data class Evidence(val word: String, val distance: Int, val lines: Int)
    data class Context(val match: MatchResult, val text: String, val start: Int, val cue: Evidence?, val blocked: Boolean) {
        fun nearest(words: Collection<String>) = nearest(text, words, match.range.first - start, match.range.last + 1 - start)
        val factor: Double get() = if (blocked || cue == null) 0.0 else
            (1.0 - 0.04 * maxOf(0, cue.distance - 2) - 0.08 * cue.lines).coerceAtLeast(0.0)
    }

    fun nearest(text: String, words: Collection<String>, start: Int, end: Int): Evidence? = words.flatMap { word ->
        Regex("(?i)(?<![a-z0-9_])${Regex.escape(word)}(?![a-z0-9_])").findAll(text).mapNotNull { hit ->
            val gap = when {
                hit.range.last < start -> text.substring(hit.range.last + 1, start)
                hit.range.first >= end -> text.substring(end, hit.range.first)
                else -> ""
            }
            val distance = tokens.findAll(gap).count()
            val lines = gap.count { it == '\n' }
            if (distance < 10 && lines <= 2) Evidence(word, distance, lines) else null
        }.toList()
    }.minWithOrNull(compareBy<Evidence> { it.distance }.thenBy { it.lines }.thenBy { it.word })

    fun find(text: String): List<Context> {
        val matches = money.findAll(text).toMutableList()
        for (pattern in listOf(bare, compact)) for (match in pattern.findAll(text)) {
            if (matches.none { match.range.first <= it.range.last && match.range.last >= it.range.first }) matches += match
        }
        val words = tokens.findAll(text).toList()
        return matches.sortedBy { it.range.first }.map { match ->
            val first = words.indexOfFirst { it.range.last >= match.range.first }
            val last = words.indexOfLast { it.range.first <= match.range.last }
            var left = first
            var right = last
            while (left > 0 && first - left < 10 && text.substring(words[left - 1].range.first, match.range.first).count { it == '\n' } <= 2) left--
            while (right + 1 < words.size && right - last < 10 && text.substring(match.range.last + 1, words[right + 1].range.last + 1).count { it == '\n' } <= 2) right++
            val start = words[left].range.first
            val local = text.substring(start, words[right].range.last + 1)
            val a = match.range.first - start
            val b = match.range.last + 1 - start
            val cue = nearest(local, inbound + outbound + documents + holds, a, b)
            val blocked = listOf(nearest(local, balances, a, b), nearest(local, noise, a, b)).any {
                it != null && (cue == null || it.distance < cue.distance || (it.distance == cue.distance && it.lines <= cue.lines))
            } || nearest(local, incomplete, a, b) != null
            Context(match, local, start, cue, blocked)
        }
    }

    fun select(text: String): Context? {
        val candidates = find(text)
        val usable = candidates.filter { !it.blocked && it.cue != null }
        return (usable.ifEmpty { candidates }).minWithOrNull(compareBy<Context> { it.blocked }.thenBy { it.cue?.distance ?: 99 }.thenBy { it.cue?.lines ?: 0 })
    }
}
