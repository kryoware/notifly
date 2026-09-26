package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.TRANSFER_WINDOW
import ph.notifly.domain.model.inboundLeg
import ph.notifly.domain.model.TransactionType
import ph.notifly.domain.model.isTransferPairWith

@Dao
interface RawCaptureDao {
    @Query("SELECT * FROM raw_captures WHERE (:result IS NULL OR result = :result) ORDER BY capturedAtMillis DESC")
    fun observeLog(result: String?): Flow<List<RawCaptureEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun record(entity: RawCaptureEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun reserveReceipt(receipt: CaptureReceiptEntity): Long

    /** Records a capture atomically, returning `-1` when its fingerprint was already reserved. */
    @androidx.room.Transaction
    suspend fun recordOnce(capture: RawCaptureEntity): Long {
        if (capture.fingerprint != null && reserveReceipt(CaptureReceiptEntity(capture.fingerprint)) == -1L) return -1L
        return record(capture)
    }

    @Insert
    suspend fun insertTransaction(entity: TransactionEntity): Long

    /**
     * Returns review drafts matching the amount in minor units and currency, newest occurrence first.
     * [sinceMillis] is an inclusive Unix epoch millisecond cutoff; there is no upper time bound.
     */
    @Query(
        "SELECT * FROM transactions WHERE status = 'NEEDS_REVIEW' AND amountMinor = :amountMinor " +
            "AND currency = :currency AND occurredAtMillis >= :sinceMillis ORDER BY occurredAtMillis DESC",
    )
    suspend fun needsReviewCandidates(amountMinor: Long, currency: String, sinceMillis: Long): List<TransactionEntity>

    /** Whether the package is marked as a finance app, regardless of its listening setting. */
    @Query("SELECT EXISTS(SELECT 1 FROM allowed_apps WHERE packageName = :packageName AND finance = 1)")
    suspend fun isFinance(packageName: String): Boolean

    @Query("SELECT sourceApp FROM raw_captures WHERE id = :id")
    suspend fun sourceAppFor(id: Long): String?

    @Query("UPDATE transactions SET type = :type, category = :category, title = :title, fromApp = :fromApp, toApp = :toApp WHERE id = :id")
    suspend fun mergeIntoTransfer(id: Long, type: String, category: String, title: String, fromApp: String, toApp: String)

    /**
     * Stores a unique capture and its review draft atomically, returning the capture ID or `-1` for a duplicate.
     *
     * If the draft is the other side of a transfer already awaiting review (same amount and
     * currency, opposite leg directions, different finance apps, at most [TRANSFER_WINDOW] apart),
     * the newest matching row is merged into a single TRANSFER with both ends set instead of inserting
     * a second draft. A merged row has no single direction, so it never pairs again. The incoming
     * capture is kept and the row remains awaiting review.
     * Database and entity-conversion failures propagate and roll back the operation.
     *
     * @throws IllegalArgumentException if [transaction] is not awaiting review.
     */
    @androidx.room.Transaction
    suspend fun recordParsed(capture: RawCaptureEntity, transaction: TransactionEntity): Long {
        require(transaction.status == "NEEDS_REVIEW")
        val id = recordOnce(capture)
        if (id == -1L) return -1L
        val incoming = transaction.toDomain().copy(captureId = id)
        val since = incoming.occurredAt.minus(TRANSFER_WINDOW).toEpochMilliseconds()
        val candidate = if (incoming.sourceApp?.let { isFinance(it) } != true) null
            else needsReviewCandidates(incoming.amountMinor, incoming.currency, since)
                .map { it.toDomain() }
                .firstOrNull { it.isTransferPairWith(incoming) && isFinance(it.sourceApp!!) }
        if (candidate == null) {
            insertTransaction(incoming.toEntity())
            return id
        }
        val (from, to) = if (incoming.inboundLeg == false) incoming to candidate else candidate to incoming
        val fromLabel = from.captureId?.let { sourceAppFor(it) } ?: from.sourceApp.orEmpty()
        val toLabel = to.captureId?.let { sourceAppFor(it) } ?: to.sourceApp.orEmpty()
        mergeIntoTransfer(candidate.id, TransactionType.TRANSFER.name, "Transfer", "$fromLabel → $toLabel", from.sourceApp!!, to.sourceApp!!)
        return id
    }

    @Query("DELETE FROM raw_captures")
    suspend fun clearLog()

    @Query("UPDATE raw_captures SET body = NULL")
    suspend fun redactBodies()

    @Query("DELETE FROM raw_captures WHERE capturedAtMillis < :cutoffMillis")
    suspend fun purgeExpired(cutoffMillis: Long)
}
