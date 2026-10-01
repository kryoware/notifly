package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["status", "occurredAtMillis", "createdAtMillis"]),
    Index("accountId"), Index("toAccountId"), Index("categoryId")], foreignKeys = [
    androidx.room.ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
    androidx.room.ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"]),
    androidx.room.ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])])
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
    @androidx.room.ColumnInfo(defaultValue = "0") val accountId: Long = 0,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val fromApp: String? = null,
    val toApp: String? = null,
)
