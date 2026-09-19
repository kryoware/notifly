package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Dedupe survives clearing/redacting the diagnostic log and deleting a transaction. */
// ponytail: receipt hashes persist until app data is cleared; revisit retention if measured volume requires it.
@Entity(tableName = "capture_receipts")
data class CaptureReceiptEntity(@PrimaryKey val fingerprint: String)
