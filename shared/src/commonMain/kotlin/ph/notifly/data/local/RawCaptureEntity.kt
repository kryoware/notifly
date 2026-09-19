package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_captures", indices = [androidx.room.Index(value = ["fingerprint"], unique = true)])
data class RawCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String,
    val capturedAtMillis: Long,
    val body: String?,
    val result: String,
    val matchedAmount: String?,
    val matchedDirection: String?,
    val reason: String,
    val fingerprint: String? = null,
)
