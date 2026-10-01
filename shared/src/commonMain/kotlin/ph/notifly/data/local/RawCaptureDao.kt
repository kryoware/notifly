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
interface RawCaptureDao : LedgerDao {
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
        return recordDraft(capture, CapturedDraftEntity(captureId = null, title = transaction.title,
            amountMinor = transaction.amountMinor, type = transaction.type,
            inbound = transaction.toDomain().inboundLeg, occurredAtMillis = transaction.occurredAtMillis,
            sourceApp = transaction.sourceApp.orEmpty(), accountId = transaction.accountId.takeIf { it > 0 },
            toAccountId = transaction.toAccountId, accountHint = null))
    }

    @androidx.room.Transaction
    suspend fun recordDraft(capture: RawCaptureEntity, draft: CapturedDraftEntity): Long {
        require(draft.amountMinor > 0 && draft.type in listOf("INCOME", "EXPENSE", "TRANSFER"))
        val captureId = recordOnce(capture)
        if (captureId == -1L) return -1L
        val incoming = draft.copy(captureId = captureId,
            accountId = draft.accountId?.takeIf { accountById(it)?.archived == false },
            toAccountId = draft.toAccountId?.takeIf { accountById(it)?.archived == false })
        val accountId = incoming.accountId
        val inbound = incoming.inbound
        val window = TRANSFER_WINDOW.inWholeMilliseconds
        val candidates = if (accountId == null || inbound == null || incoming.toAccountId != null) emptyList()
        else needsReviewCandidates(incoming.amountMinor, "PHP", incoming.occurredAtMillis - window).map { it.toDomain() }.filter {
            it.captureId != null && it.accountId != accountId && it.inboundLeg == !inbound &&
                (it.occurredAt.toEpochMilliseconds() - incoming.occurredAtMillis) in -window..window
        }
        val pending = if (accountId == null || inbound == null || incoming.toAccountId != null) emptyList()
        else drafts().filter { it.accountId != null && it.accountId != accountId && it.inbound == !inbound &&
            it.toAccountId == null && it.amountMinor == incoming.amountMinor &&
            (it.occurredAtMillis - incoming.occurredAtMillis) in -window..window }
        if (candidates.size + pending.size == 1) {
            val other = candidates.singleOrNull()
            val otherDraft = pending.singleOrNull()
            val otherAccountId = other?.accountId ?: otherDraft!!.accountId!!
            val from = if (inbound == false) accountId!! else otherAccountId
            val to = if (inbound == true) accountId!! else otherAccountId
            val fromName = accountById(from)?.name ?: "Account"
            val toName = accountById(to)?.name ?: "Account"
            saveLocally(TransactionEntity(id = other?.id ?: 0, title = "$fromName → $toName",
                amountMinor = incoming.amountMinor, currency = "PHP", type = "TRANSFER", status = "NEEDS_REVIEW",
                category = "Transfer", occurredAtMillis = other?.occurredAt?.toEpochMilliseconds() ?: incoming.occurredAtMillis,
                sourceApp = other?.sourceApp ?: incoming.sourceApp, captureId = other?.captureId ?: incoming.captureId,
                note = "", accountId = from, toAccountId = to,
                fromApp = if (inbound == false) incoming.sourceApp else other?.sourceApp ?: otherDraft?.sourceApp,
                toApp = if (inbound == true) incoming.sourceApp else other?.sourceApp ?: otherDraft?.sourceApp))
            otherDraft?.let { deleteDraft(it.id) }
        } else if (accountId != null && (incoming.type != "TRANSFER" || incoming.toAccountId != null) && inbound != null) {
            saveLocally(TransactionEntity(title = incoming.title, amountMinor = incoming.amountMinor, currency = "PHP",
                type = incoming.type, status = "NEEDS_REVIEW", category = if (incoming.type == "TRANSFER") "Transfer" else "Other",
                occurredAtMillis = incoming.occurredAtMillis, sourceApp = incoming.sourceApp,
                captureId = captureId, note = "", accountId = accountId, toAccountId = incoming.toAccountId))
        } else insertDraft(incoming)
        return captureId
    }

    @Query("DELETE FROM raw_captures")
    suspend fun clearLog()

    @Query("UPDATE raw_captures SET body = NULL")
    suspend fun redactBodies()

    @Query("DELETE FROM raw_captures WHERE capturedAtMillis < :cutoffMillis")
    suspend fun purgeExpired(cutoffMillis: Long)
}
