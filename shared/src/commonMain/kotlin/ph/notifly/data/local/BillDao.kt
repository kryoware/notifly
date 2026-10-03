package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY startsOnDay, name COLLATE NOCASE") fun observeBills(): Flow<List<BillEntity>>
    @Query("SELECT * FROM bill_payments ORDER BY dueOnDay") fun observePayments(): Flow<List<BillPaymentEntity>>
    @Query("SELECT * FROM bills WHERE id = :id") suspend fun byId(id: Long): BillEntity?
    @Query("SELECT * FROM bill_payments WHERE id = :id") suspend fun paymentById(id: Long): BillPaymentEntity?
    @Query("SELECT * FROM bill_payments WHERE billId = :id") suspend fun paymentsFor(id: Long): List<BillPaymentEntity>
    /** Returns one other confirmed bill with an exact app match and a case-insensitive name match, or null. */
    @Query("SELECT * FROM bills WHERE status = 'CONFIRMED' AND id != :excluding AND sourceApp = :sourceApp AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun activeFrom(sourceApp: String, name: String, excluding: Long): BillEntity?
    /** Counts bills of any status matching the app, case-insensitive name, and start date in epoch days. */
    @Query("SELECT COUNT(*) FROM bills WHERE sourceApp = :sourceApp AND name = :name COLLATE NOCASE AND startsOnDay = :day")
    suspend fun duplicates(sourceApp: String, name: String, day: Long): Int
    @Upsert suspend fun upsert(bill: BillEntity): Long
    @Insert suspend fun insertPayment(payment: BillPaymentEntity): Long
    @Query("DELETE FROM bills WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM bill_payments WHERE id = :id") suspend fun deletePayment(id: Long)
    @Query("UPDATE bills SET settled = :settled WHERE id = :id") suspend fun setSettled(id: Long, settled: Int)
    @Query("UPDATE bills SET status = 'CONFIRMED' WHERE id = :id") suspend fun markConfirmed(id: Long)
    @Query("UPDATE bills SET remindedForDay = :day WHERE id = :id") suspend fun markReminded(id: Long, day: Long)

    /**
     * Confirms the bill or merges its amount into a matching confirmed bill, clearing the reminder marker.
     * A merge preserves the existing schedule and deletes the draft; a missing ID does nothing. Storage failures propagate.
     */
    @androidx.room.Transaction
    suspend fun save(bill: BillEntity): Long {
        val previous = if (bill.id != 0L) byId(bill.id) else null
        val id = upsert(bill)
        if (previous != null && (previous.startsOnDay != bill.startsOnDay || previous.repeats != bill.repeats)) {
            setSettled(bill.id, contiguousSettled(bill.id, bill.toDomain()))
        }
        return id
    }

    @androidx.room.Transaction
    suspend fun confirm(id: Long) {
        val draft = byId(id) ?: return
        val existing = draft.sourceApp?.let { activeFrom(it, draft.name, id) }
        if (existing == null) { markConfirmed(id); return }
        val updated = existing.copy(amountMinor = draft.amountMinor, remindedForDay = null)
        save(updated)
        delete(id)
    }

    /**
     * Upserts the supplied row unless its nonnull app, case-insensitive name, and start date already exist.
     * Returns false for a duplicate; null app names bypass deduplication. Status is preserved; storage failures propagate.
     */
    @androidx.room.Transaction
    suspend fun recordDetected(bill: BillEntity): Boolean {
        val source = bill.sourceApp
        if (source != null && duplicates(source, bill.name, bill.startsOnDay) > 0) return false
        upsert(bill)
        return true
    }

    /**
    * Records the supplied unrecorded occurrence at or after the settled count, then recomputes that count.
    * A null [transactionId] records a skip; [settledAtMillis] is milliseconds since the Unix epoch.
    * Returns the payment ID, or -1 when the bill is missing or [dueOn] is not an available occurrence.
    * Storage, row-conversion, and date arithmetic failures propagate.
     */
    @androidx.room.Transaction
    suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?, settledAtMillis: Long): Long {
        val bill = byId(billId)?.toDomain() ?: return -1
        val paymentDays = paymentsFor(billId).map { it.dueOnDay }.toSet()
        val index = if (bill.repeat == ph.notifly.domain.model.BillRepeat.ONCE) {
            0.takeIf { bill.occurrence(0) == dueOn }
        } else generateSequence(bill.settled) { it + 1 }
            .takeWhile { bill.occurrence(it) <= dueOn }
            .firstOrNull { bill.occurrence(it) == dueOn }
        if (index == null || dueOn.toEpochDays() in paymentDays) return -1
        val id = insertPayment(BillPaymentEntity(billId = billId, dueOnDay = dueOn.toEpochDays(), transactionId = transactionId, settledAtMillis = settledAtMillis))
        setSettled(billId, contiguousSettled(billId, bill))
        return id
    }

    /**
     * Removes a payment or skip and recomputes the bill's contiguous settled count.
     * Missing payments do nothing; linked transactions are untouched. Storage and date arithmetic failures propagate.
     */
    @androidx.room.Transaction
    suspend fun unsettle(paymentId: Long) {
        val payment = paymentById(paymentId) ?: return
        deletePayment(paymentId)
        byId(payment.billId)?.let { bill ->
            setSettled(bill.id, contiguousSettled(bill.id, bill.toDomain()))
        }
    }

    /**
     * Removes every payment linked to the transaction and recomputes affected bills' settled counts.
     * Does not delete the transaction itself. Storage and date arithmetic failures propagate.
     */
    @androidx.room.Transaction
    suspend fun transactionDeleted(transactionId: Long) {
        val affected = paymentsForTransaction(transactionId)
        affected.forEach { payment ->
            deletePayment(payment.id)
            byId(payment.billId)?.let { bill -> setSettled(bill.id, contiguousSettled(bill.id, bill.toDomain())) }
        }
    }

    @Query("SELECT * FROM bill_payments WHERE transactionId = :id") suspend fun paymentsForTransaction(id: Long): List<BillPaymentEntity>

    /**
     * Counts consecutive occurrences with a payment or skip, starting at the supplied bill's anchor.
     * Stops at the first gap; a one-time bill yields zero or one. Storage and date arithmetic failures propagate.
     */
    suspend fun contiguousSettled(id: Long, bill: ph.notifly.domain.model.Bill): Int {
        val dates = paymentsFor(id).map { it.dueOnDay }.toSet()
        if (bill.repeat == ph.notifly.domain.model.BillRepeat.ONCE) return if (bill.startsOn.toEpochDays() in dates) 1 else 0
        return generateSequence(0) { it + 1 }.takeWhile { bill.occurrence(it).toEpochDays() in dates }.count()
    }
}
