package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Replacing a pending change creates a new id, so stale acknowledgements cannot lose edits. */
@Entity(tableName = "pending_changes", indices = [Index(value = ["transactionId"], unique = true)])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val operation: String,
)
