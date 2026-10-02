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
    @Query("SELECT * FROM bills WHERE status = 'CONFIRMED' AND id != :excluding AND sourceApp = :sourceApp AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun activeFrom(sourceApp: String, name: String, excluding: Long): BillEntity?
    @Query("SELECT COUNT(*) FROM bills WHERE sourceApp = :sourceApp AND name = :name COLLATE NOCASE AND startsOnDay = :day")
    suspend fun duplicates(sourceApp: String, name: String, day: Long): Int
    @Upsert suspend fun upsert(bill: BillEntity): Long
    @Insert suspend fun insertPayment(payment: BillPaymentEntity): Long
    @Query("DELETE FROM bills WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM bill_payments WHERE id = :id") suspend fun deletePayment(id: Long)
    @Query("UPDATE bills SET settled = :settled WHERE id = :id") suspend fun setSettled(id: Long, settled: Int)
    @Query("UPDATE bills SET status = 'CONFIRMED' WHERE id = :id") suspend fun markConfirmed(id: Long)
    @Query("UPDATE bills SET remindedForDay = :day WHERE id = :id") suspend fun markReminded(id: Long, day: Long)

    @androidx.room.Transaction
    suspend fun confirm(id: Long) {
        val draft = byId(id) ?: return
        val existing = draft.sourceApp?.let { activeFrom(it, draft.name, id) }
        if (existing == null) { markConfirmed(id); return }
        upsert(existing.copy(amountMinor = draft.amountMinor, remindedForDay = null))
        delete(id)
    }

    @androidx.room.Transaction
    suspend fun recordDetected(bill: BillEntity): Boolean {
        val source = bill.sourceApp
        if (source != null && duplicates(source, bill.name, bill.startsOnDay) > 0) return false
        upsert(bill)
        return true
    }

    /** Returns -1 when [dueOn] is no longer the bill's next due date, so a double tap cannot settle twice. */
    @androidx.room.Transaction
    suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?, settledAtMillis: Long): Long {
        val bill = byId(billId)?.toDomain() ?: return -1
        val paymentDays = paymentsFor(billId).map { it.dueOnDay }.toSet()
        val next = if (bill.repeat == ph.notifly.domain.model.BillRepeat.ONCE) {
            bill.occurrence(0).takeIf { it.toEpochDays() !in paymentDays }
        } else generateSequence(bill.settled) { it + 1 }
            .firstOrNull { bill.occurrence(it).toEpochDays() !in paymentDays }
            ?.let(bill::occurrence)
        if (next != dueOn) return -1
        val id = insertPayment(BillPaymentEntity(billId = billId, dueOnDay = dueOn.toEpochDays(), transactionId = transactionId, settledAtMillis = settledAtMillis))
        setSettled(billId, contiguousSettled(billId, bill))
        return id
    }

    @androidx.room.Transaction
    suspend fun unsettle(paymentId: Long) {
        val payment = paymentById(paymentId) ?: return
        deletePayment(paymentId)
        byId(payment.billId)?.let { bill ->
            setSettled(bill.id, contiguousSettled(bill.id, bill.toDomain()))
        }
    }

    @androidx.room.Transaction
    suspend fun transactionDeleted(transactionId: Long) {
        val affected = paymentsForTransaction(transactionId)
        affected.forEach { payment ->
            deletePayment(payment.id)
            byId(payment.billId)?.let { bill -> setSettled(bill.id, contiguousSettled(bill.id, bill.toDomain())) }
        }
    }

    @Query("SELECT * FROM bill_payments WHERE transactionId = :id") suspend fun paymentsForTransaction(id: Long): List<BillPaymentEntity>

    suspend fun contiguousSettled(id: Long, bill: ph.notifly.domain.model.Bill): Int {
        val dates = paymentsFor(id).map { it.dueOnDay }.toSet()
        if (bill.repeat == ph.notifly.domain.model.BillRepeat.ONCE) return if (bill.startsOn.toEpochDays() in dates) 1 else 0
        return generateSequence(0) { it + 1 }.takeWhile { bill.occurrence(it).toEpochDays() in dates }.count()
    }
}
