package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ph.notifly.domain.model.Bill
import ph.notifly.domain.model.BillPayment

interface BillRepository {
    fun observeBills(): Flow<List<Bill>>
    fun observePayments(): Flow<List<BillPayment>>
    suspend fun byId(id: Long): Bill?
    suspend fun save(bill: Bill): Long
    suspend fun delete(id: Long)
    /**
     * Confirms a detected bill. If an active bill from the same app with the same name exists,
     * that bill takes the new amount and next due date instead, and the draft is removed.
     */
    suspend fun confirm(id: Long)
    /** Stores a detected bill as NEEDS_REVIEW. Returns false for a duplicate of app + name + due date. */
    suspend fun recordDetected(bill: Bill): Boolean
    /** Records [dueOn] as paid by [transactionId], or skipped when null, and advances the bill. Returns the payment id. */
    suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?): Long
    /** Reverses [settle]; backs Undo. */
    suspend fun unsettle(paymentId: Long)
    suspend fun markReminded(id: Long, due: LocalDate)
}
