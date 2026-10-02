package ph.notifly.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class BillRepeat { ONCE, WEEKLY, MONTHLY, YEARLY }

/**
 * Something the user has to pay by a date. A due notice is not a money movement, so a bill is
 * never a [Transaction]; paying it links to one. Detected bills start NEEDS_REVIEW and stay out of
 * totals and the calendar until the user confirms them. Bills never sync.
 */
data class Bill(
    val id: Long = 0,
    val name: String,
    val amountMinor: Long,          // centavos. Never Double for money.
    val category: String = "Bills",
    val accountId: Long? = null,
    val startsOn: LocalDate,
    val repeat: BillRepeat,
    val settled: Int = 0,           // occurrences paid or skipped; nextDue = occurrence(settled)
    val status: TransactionStatus,  // NEEDS_REVIEW while a detected bill awaits the user
    val detected: Boolean = false,  // true when on-device capture proposed it
    val sourceApp: String? = null,
    val captureId: Long? = null,
    val remindedFor: LocalDate? = null,
    val createdAt: Instant,
) {
    /** Always computed from the anchor, so Jan 31 gives Feb 28 and then Mar 31 with no drift. */
    fun occurrence(n: Int): LocalDate = when (repeat) {
        BillRepeat.ONCE -> startsOn
        BillRepeat.WEEKLY -> startsOn.plus(n, DateTimeUnit.WEEK)
        BillRepeat.MONTHLY -> startsOn.plus(n, DateTimeUnit.MONTH)
        BillRepeat.YEARLY -> startsOn.plus(n, DateTimeUnit.YEAR)
    }

    /** The next unpaid occurrence, or null once a one-time bill is settled. */
    val nextDue: LocalDate? get() = if (repeat == BillRepeat.ONCE && settled >= 1) null else occurrence(settled)

    fun validate() {
        require(name.isNotBlank()) { "Enter a bill name." }
        require(amountMinor > 0) { "Enter an amount above zero." }
    }
}

/** [transactionId] null means the occurrence was skipped, not paid. */
data class BillPayment(val id: Long = 0, val billId: Long, val dueOn: LocalDate,
    val transactionId: Long?, val settledAt: Instant)

data class BillOccurrence(val bill: Bill, val dueOn: LocalDate, val payment: BillPayment?) {
    val paid get() = payment != null
}

/** Confirmed bills only. Recurring occurrences are projected here and never stored. Skipped ones are omitted. */
fun billOccurrences(bills: List<Bill>, payments: List<BillPayment>, from: LocalDate, to: LocalDate): List<BillOccurrence> {
    val byBill = payments.groupBy { it.billId }
    return bills.filter { it.status == TransactionStatus.CONFIRMED }.flatMap { bill ->
        val made = byBill[bill.id].orEmpty().associateBy { it.dueOn }
        var low = 0
        var high = 1
        if (bill.repeat == BillRepeat.ONCE) low = if (bill.startsOn < from) 1 else 0
        else {
            while (bill.occurrence(high) < from && high < Int.MAX_VALUE / 2) high *= 2
            while (low < high) {
                val middle = low + (high - low) / 2
                if (bill.occurrence(middle) < from) low = middle + 1 else high = middle
            }
        }
        val scheduled = generateSequence(low) { it + 1 }.take(if (bill.repeat == BillRepeat.ONCE) 1 - low else Int.MAX_VALUE - low).map { n -> n to bill.occurrence(n) }
            .takeWhile { it.second <= to }
            .mapNotNull { (n, due) ->
                val payment = made[due]
                when {
                    n < bill.settled && payment == null -> null
                    payment != null && payment.transactionId == null -> null
                    else -> BillOccurrence(bill, due, payment)
                }
            }.toList()
        val scheduledDates = scheduled.mapTo(mutableSetOf()) { it.dueOn }
        (scheduled + made.values.asSequence().filter { it.dueOn in from..to && it.dueOn !in scheduledDates }
            .filter { it.transactionId != null }
            .map { BillOccurrence(bill, it.dueOn, it) }.toList())
    }.sortedWith(compareBy({ it.dueOn }, { it.bill.name.lowercase() }))
}

data class BillDue(val bill: Bill, val dueOn: LocalDate)

data class UpcomingBills(val overdue: List<BillDue>, val thisWeek: List<BillDue>, val later: List<BillDue>) {
    val all get() = overdue + thisWeek + later
}

/** One entry per confirmed bill, at its next unpaid occurrence. This week is today through today + 6. */
fun upcoming(bills: List<Bill>, today: LocalDate): UpcomingBills {
    val due = bills.filter { it.status == TransactionStatus.CONFIRMED }
        .mapNotNull { bill -> bill.nextDue?.let { BillDue(bill, it) } }
        .sortedWith(compareBy({ it.dueOn }, { it.bill.name.lowercase() }))
    val weekEnd = today.plus(6, DateTimeUnit.DAY)
    return UpcomingBills(
        overdue = due.filter { it.dueOn < today },
        thisWeek = due.filter { it.dueOn >= today && it.dueOn <= weekEnd },
        later = due.filter { it.dueOn > weekEnd },
    )
}

/** The next 30 days of unpaid bills, plus what is already late. Overdue is kept out of the 30-day total. */
data class BillSummary(val dueSoonMinor: Long, val dueSoonCount: Int, val next: BillDue?, val overdueMinor: Long, val overdueCount: Int)

fun UpcomingBills.summary(today: LocalDate): BillSummary {
    val soon = (thisWeek + later).filter { it.dueOn <= today.plus(30, DateTimeUnit.DAY) }
    return BillSummary(soon.sumOf { it.bill.amountMinor }, soon.size, soon.firstOrNull(),
        overdue.sumOf { it.bill.amountMinor }, overdue.size)
}

/**
 * Unpaid expenses dated 10 days before to 3 days after [due] that look like this bill: same amount,
 * or a title containing the bill name. Exact amount ranks first, then title match, then nearness.
 */
fun paymentCandidates(bill: Bill, due: LocalDate, transactions: List<Transaction>,
    payments: List<BillPayment>, zone: TimeZone = TimeZone.currentSystemDefault(), limit: Int = 3): List<Transaction> {
    val linked = payments.mapNotNull { it.transactionId }.toSet()
    val from = due.minus(10, DateTimeUnit.DAY)
    val to = due.plus(3, DateTimeUnit.DAY)
    fun Transaction.day() = occurredAt.toLocalDateTime(zone).date
    return transactions.asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.id !in linked && it.day() in from..to }
        .map { it to (it.amountMinor == bill.amountMinor) }
        .map { (t, exact) -> Triple(t, exact, t.title.contains(bill.name, ignoreCase = true)) }
        .filter { (_, exact, named) -> exact || named }
        .sortedWith(compareBy({ !it.second }, { !it.third }, { kotlin.math.abs(it.first.day().toEpochDays() - due.toEpochDays()) }))
        .map { it.first }.take(limit).toList()
}
