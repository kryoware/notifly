package ph.notifly.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val kind: String,
    val listening: Boolean,
    val capturedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val finance: Boolean = false,
)
