package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY occurredAtMillis DESC")
    fun observeByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Upsert
    suspend fun upsert(entity: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun enqueue(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes ORDER BY id")
    suspend fun pendingChanges(): List<PendingChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_changes WHERE id = :changeId")
    suspend fun acknowledge(changeId: Long)

    @androidx.room.Transaction
    suspend fun saveLocally(entity: TransactionEntity): Long {
        require(entity.title.isNotBlank() && entity.amountMinor > 0)
        val previous = if (entity.id != 0L) byId(entity.id) else null
        val inserted = upsert(entity)
        val id = if (entity.id == 0L) inserted else entity.id
        if (entity.status == "CONFIRMED") enqueue(PendingChangeEntity(transactionId = id, operation = "UPSERT"))
        else if (previous?.status == "CONFIRMED") enqueue(PendingChangeEntity(transactionId = id, operation = "DELETE"))
        return id
    }

    @androidx.room.Transaction
    suspend fun deleteLocally(id: Long) {
        val previous = byId(id) ?: return
        delete(id)
        if (previous.status == "CONFIRMED") enqueue(PendingChangeEntity(transactionId = id, operation = "DELETE"))
    }

    /** CONFIRMED only — callers must never include NEEDS_REVIEW here. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor
                                  WHEN type = 'EXPENSE' THEN -amountMinor
                                  ELSE 0 END), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
        """
    )
    fun observeConfirmedNetMinor(): Flow<Long>
}
