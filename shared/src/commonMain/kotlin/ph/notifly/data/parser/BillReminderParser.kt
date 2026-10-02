package ph.notifly.data.parser

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class BillDraft(val name: String, val amountMinor: Long, val dueOn: LocalDate)

/**
 * Spots bill-due notices. A due notice is not a money movement, so it never becomes a transaction draft.
 *
 * Needs all of: a bill cue, an amount, and a due date. Anything missing => null; nothing is invented.
 * A notice that reports a payment already made is never a bill.
 */
class BillReminderParser(private val amounts: NotificationParser = NotificationParser()) {
    private val cue = Regex(
        """(?i)\b(?:due|due date|amount due|minimum amount due|statement|pay on or before|bayaran bago|huling araw)\b""",
    )
    private val settled = Regex("""(?i)\b(?:received|successful(?:ly)?|paid|thank you for (?:your )?payment)\b""")
    private val dueCue = Regex("""(?i)(?:\bdue(?: date)?|on or before|\bbago|huling araw)\W+(?:\w+\W+){0,2}$""")
    private val amountCue = Regex("""(?i)\b(?:minimum amount due|total amount due|amount due|due amount|amount to pay)\b\W{0,6}""")
    private val billName = Regex("""(?i)\b(?:your|iyong|inyong)\s+([A-Za-z][\w&.' -]{1,38}?)\s+(?:e-?statement\s+)?bill\b""")
    private val leadingNoise = Regex("""(?i)^(?:(?:latest|new|current|monthly|total|unpaid)\s+)+""")

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthName = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val monthFirst = Regex("""(?i)\b$monthName\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4})\b)?""")
    private val dayFirst = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+$monthName\b\.?(?:,?\s+(\d{4})\b)?""")
    private val slashed = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

    /**
     * Returns a bill draft with an amount in centavos, using [appLabel] when no bill name is found.
     * Requires a bill cue, a positive amount, and a valid date; payment-completion wording returns null.
     * Prefers an amount following an amount-due cue, falling back to the first amount in [body].
     * Dates without a year are tried in [today]'s year and the next, accepting the first valid date
     * on or after today minus seven days. Missing or invalid amounts and dates yield null.
     */
    fun parse(appLabel: String, body: String, today: LocalDate): BillDraft? {
        if (!cue.containsMatchIn(body) || settled.containsMatchIn(body)) return null
        val amountMinor = amountCue.find(body)?.let { amounts.firstAmountMinor(body, it.range.first) }
            ?: amounts.firstAmountMinor(body) ?: return null
        if (amountMinor <= 0) return null
        val dueOn = dueDate(body, today) ?: return null
        val named = billName.find(body)?.groupValues?.get(1)?.replace(leadingNoise, "")?.trim()
        return BillDraft(name = named?.takeIf { it.isNotBlank() } ?: appLabel, amountMinor = amountMinor, dueOn = dueOn)
    }

    /**
     * Returns the earliest valid date in the text preceded by a due cue, falling back to the first valid date.
     * Accepts English month names, ISO dates, and slash dates (month first unless the first number exceeds 12).
     */
    private fun dueDate(body: String, today: LocalDate): LocalDate? {
        val found = buildList {
            monthFirst.findAll(body).forEach { m ->
                add(m.range.first to date(m.groupValues[1], m.groupValues[2], m.groupValues[3], today))
            }
            dayFirst.findAll(body).forEach { m ->
                add(m.range.first to date(m.groupValues[2], m.groupValues[1], m.groupValues[3], today))
            }
            slashed.findAll(body).forEach { m ->
                val (a, b, y) = m.destructured
                val dayFirstForm = a.toInt() > 12
                add(m.range.first to date(if (dayFirstForm) b else a, if (dayFirstForm) a else b, y, today))
            }
            iso.findAll(body).forEach { m ->
                add(m.range.first to date(m.groupValues[2], m.groupValues[3], m.groupValues[1], today))
            }
        }.filter { it.second != null }.sortedBy { it.first }
        // Statement notices carry other dates (statement date); prefer the one the notice calls the due date.
        return (found.firstOrNull { dueCue.containsMatchIn(body.substring(0, it.first).takeLast(40)) } ?: found.firstOrNull())?.second
    }

    /**
     * [month] is a name or number; invalid explicit dates such as Feb 30 return null.
     * An empty [year] tries this year and next, accepting dates on or after [today] minus seven days.
     * Invalid date construction is caught; failures computing that seven-day cutoff propagate.
     */
    private fun date(month: String, day: String, year: String, today: LocalDate): LocalDate? {
        val m = month.toIntOrNull() ?: (months.indexOf(month.lowercase().take(3)) + 1).takeIf { it > 0 } ?: return null
        val d = day.toIntOrNull() ?: return null
        if (year.isNotEmpty()) return runCatching { LocalDate(year.toInt(), m, d) }.getOrNull()
        val earliest = today.minus(7, DateTimeUnit.DAY)
        return listOf(today.year, today.year + 1).firstNotNullOfOrNull { y ->
            runCatching { LocalDate(y, m, d) }.getOrNull()?.takeIf { it >= earliest }
        }
    }
}
