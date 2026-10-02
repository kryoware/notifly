package ph.notifly.data.repository

import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import ph.notifly.data.local.BillDao
import ph.notifly.data.local.toDomain
import ph.notifly.data.local.toEntity
import ph.notifly.domain.model.Bill
import ph.notifly.domain.repository.BillRepository
import kotlin.time.Clock

class BillRepositoryImpl(private val dao: BillDao) : BillRepository {
    override fun observeBills() = dao.observeBills().map { rows -> rows.map { it.toDomain() } }
    override fun observePayments() = dao.observePayments().map { rows -> rows.map { it.toDomain() } }
    override suspend fun byId(id: Long) = dao.byId(id)?.toDomain()
    override suspend fun save(bill: Bill): Long {
        bill.validate()
        val rowId = dao.save(bill.toEntity())
        return if (bill.id == 0L) rowId else bill.id
    }
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun confirm(id: Long) = dao.confirm(id)
    /** Validates and inserts with a new ID, preserving status; a null source app bypasses duplicate detection. */
    override suspend fun recordDetected(bill: Bill): Boolean {
        bill.validate()
        return dao.recordDetected(bill.copy(id = 0).toEntity())
    }
    override suspend fun settle(billId: Long, dueOn: LocalDate, transactionId: Long?) =
        dao.settle(billId, dueOn, transactionId, Clock.System.now().toEpochMilliseconds())
    override suspend fun unsettle(paymentId: Long) = dao.unsettle(paymentId)
    override suspend fun markReminded(id: Long, due: LocalDate) = dao.markReminded(id, due.toEpochDays())
}
