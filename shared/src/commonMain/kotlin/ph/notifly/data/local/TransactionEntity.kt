package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["status", "occurredAtMillis", "createdAtMillis"])])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val status: String,
    val category: String,
    val occurredAtMillis: Long,
    val createdAtMillis: Long = occurredAtMillis,
    val sourceApp: String?,
    val captureId: Long?,
    val note: String,
)
