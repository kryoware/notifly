package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RawCaptureDao {
    @Query("SELECT * FROM raw_captures WHERE (:result IS NULL OR result = :result) ORDER BY capturedAtMillis DESC")
    fun observeLog(result: String?): Flow<List<RawCaptureEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun record(entity: RawCaptureEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun reserveReceipt(receipt: CaptureReceiptEntity): Long

    @androidx.room.Transaction
    suspend fun recordOnce(capture: RawCaptureEntity): Long {
        if (capture.fingerprint != null && reserveReceipt(CaptureReceiptEntity(capture.fingerprint)) == -1L) return -1L
        return record(capture)
    }

    @Insert
    suspend fun insertTransaction(entity: TransactionEntity): Long

    @androidx.room.Transaction
    suspend fun recordParsed(capture: RawCaptureEntity, transaction: TransactionEntity): Long {
        require(transaction.status == "NEEDS_REVIEW")
        val id = recordOnce(capture)
        if (id != -1L) insertTransaction(transaction.copy(captureId = id))
        return id
    }

    @Query("DELETE FROM raw_captures")
    suspend fun clearLog()

    @Query("UPDATE raw_captures SET body = NULL")
    suspend fun redactBodies()

    @Query("DELETE FROM raw_captures WHERE capturedAtMillis < :cutoffMillis")
    suspend fun purgeExpired(cutoffMillis: Long)
}
