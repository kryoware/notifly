package ph.notifly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val kind: String,
    val listening: Boolean,
    val capturedCount: Int = 0,
)
