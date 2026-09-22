package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps")
    fun observeAll(): Flow<List<AllowedAppEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_apps WHERE packageName = :packageName AND listening = 1)")
    suspend fun isAllowed(packageName: String): Boolean

    @Upsert
    suspend fun upsert(entity: AllowedAppEntity)

    /** Inserts newly discovered packages without overwriting listening state or capture counts. */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun addInstalled(entities: List<AllowedAppEntity>)

    @Query("UPDATE allowed_apps SET listening = :listening WHERE packageName = :packageName")
    suspend fun setListening(packageName: String, listening: Boolean)

    @Query("UPDATE allowed_apps SET capturedCount = capturedCount + 1 WHERE packageName = :packageName")
    suspend fun incrementCapturedCount(packageName: String)
}
