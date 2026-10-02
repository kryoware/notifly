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
        upsert(existing.copy(amountMinor = draft.amountMinor, startsOnDay = draft.startsOnDay, settled = 0, remindedForDay = null))
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
        if (bill.nextDue != dueOn) return -1
        setSettled(billId, bill.settled + 1)
        return insertPayment(BillPaymentEntity(billId = billId, dueOnDay = dueOn.toEpochDays(), transactionId = transactionId, settledAtMillis = settledAtMillis))
    }

    @androidx.room.Transaction
    suspend fun unsettle(paymentId: Long) {
        val payment = paymentById(paymentId) ?: return
        deletePayment(paymentId)
        byId(payment.billId)?.let { setSettled(it.id, maxOf(0, it.settled - 1)) }
    }
}
