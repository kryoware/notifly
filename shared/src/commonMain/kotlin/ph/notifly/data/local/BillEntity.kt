package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import ph.notifly.domain.model.Bill
import ph.notifly.domain.model.BillPayment
import ph.notifly.domain.model.BillRepeat
import ph.notifly.domain.model.TransactionStatus
import kotlin.time.Instant

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val category: String,
    val accountId: Long?,
    val startsOnDay: Long,
    val repeats: String,
    val settled: Int,
    val status: String,
    val detected: Boolean,
    val sourceApp: String?,
    val captureId: Long?,
    val remindedForDay: Long?,
    val createdAtMillis: Long,
)

@Entity(tableName = "bill_payments", indices = [Index("billId")], foreignKeys = [
    ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)])
data class BillPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val dueOnDay: Long,
    val transactionId: Long?,
    val settledAtMillis: Long,
)

/**
 * Restores dates and timestamps from epoch days and milliseconds.
 *
 * @throws IllegalArgumentException if a stored enum name or date is invalid.
 */
fun BillEntity.toDomain() = Bill(id, name, amountMinor, category, accountId, LocalDate.fromEpochDays(startsOnDay),
    BillRepeat.valueOf(repeats), settled, TransactionStatus.valueOf(status), detected, sourceApp, captureId,
    remindedForDay?.let(LocalDate::fromEpochDays), Instant.fromEpochMilliseconds(createdAtMillis))

/** Encodes dates as epoch days, timestamps as epoch milliseconds, and enums by name. */
fun Bill.toEntity() = BillEntity(id, name, amountMinor, category, accountId, startsOn.toEpochDays(), repeat.name,
    settled, status.name, detected, sourceApp, captureId, remindedFor?.toEpochDays(), createdAt.toEpochMilliseconds())

/**
 * Restores the due date from epoch days and settlement time from epoch milliseconds.
 *
 * @throws IllegalArgumentException if the stored due date is outside the supported range.
 */
fun BillPaymentEntity.toDomain() = BillPayment(id, billId, LocalDate.fromEpochDays(dueOnDay), transactionId,
    Instant.fromEpochMilliseconds(settledAtMillis))
