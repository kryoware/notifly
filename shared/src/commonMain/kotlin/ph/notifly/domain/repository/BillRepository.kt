package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ph.notifly.domain.model.Bill
import ph.notifly.domain.model.BillPayment

interface BillRepository {
    /** Observes all bills, including detections awaiting review. */
    fun observeBills(): Flow<List<Bill>>
    /** Observes payment and skip records for bill occurrences. */
    fun observePayments(): Flow<List<BillPayment>>
    /** Returns the bill, or null if it no longer exists. */
    suspend fun byId(id: Long): Bill?
    /**
     * Saves a bill and returns its ID; zero requests a new ID. Storage failures propagate.
     *
     * @throws IllegalArgumentException if the name is blank or the amount in centavos is not positive.
     */
    suspend fun save(bill: Bill): Long
    /** Deletes the bill and its payment/skip records, leaving linked transactions intact; missing IDs are ignored. */
    suspend fun delete(id: Long)
    /**
     * Confirms a bill. If another confirmed bill from the same app has the same name
     * ignoring case, updates its amount and clears its reminder marker, preserving its schedule,
     * then deletes the draft and its payment records. Missing IDs are ignored; storage failures propagate.
     */
    suspend fun confirm(id: Long)
    /**
     * Stores a detected bill with a new ID, preserving the supplied status; callers provide NEEDS_REVIEW.
     * Returns false for a duplicate app, case-insensitive name, and start date. Storage failures propagate.
     *
     * @throws IllegalArgumentException if the name is blank or the amount in centavos is not positive.
     */
    suspend fun recordDetected(bill: Bill): Boolean
    /**
     * Records [dueOn] as paid by [transactionId], or skipped when null, and advances the bill.
     * Returns the payment ID, or -1 if the bill is missing or the occurrence is no longer eligible.
     * Does not create or confirm the linked transaction. Storage failures propagate.
     */
    suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?): Long
    /** Reverses [settle] by removing a payment or skip and adjusting the bill's settled count; missing IDs are ignored. Leaves transactions intact. */
    suspend fun unsettle(paymentId: Long)
    /** Records the due date already notified, so reminders can suppress that occurrence; missing IDs are ignored. */
    suspend fun markReminded(id: Long, due: LocalDate)
}
