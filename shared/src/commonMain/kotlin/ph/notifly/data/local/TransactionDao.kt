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

    /** CONFIRMED only — callers must never include NEEDS_REVIEW here. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor
                                  WHEN type = 'EXPENSE' THEN -amountMinor
                                  ELSE 0 END), 0)
        FROM transactions
        WHERE status = 'CONFIRMED' AND currency = 'PHP'
        """
    )
    fun observeConfirmedNetMinor(): Flow<Long>
}
