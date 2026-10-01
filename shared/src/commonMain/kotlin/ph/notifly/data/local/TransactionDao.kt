package ph.notifly.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY occurredAtMillis DESC")
    fun observeByStatus(status: String): Flow<List<TransactionEntity>>

    /**
     * Observes confirmed rows at or after [sinceMillis] (Unix epoch milliseconds), ordered by
     * occurrence then creation, newest first. [limit] is a row cap; zero returns none, negative is unlimited.
     */
    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED' AND occurredAtMillis >= :sinceMillis ORDER BY occurredAtMillis DESC, createdAtMillis DESC LIMIT :limit")
    fun observeConfirmedSince(sinceMillis: Long, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    @androidx.room.Transaction
    suspend fun importDrafts(entities: List<TransactionEntity>): Int {
        fun TransactionEntity.draft() = copy(id = 0, captureId = null, status = "NEEDS_REVIEW")
        // ponytail: dedupe loads the ledger into memory; persist import fingerprints if large ledgers make this costly.
        val known = all().map { it.draft() }.toMutableSet()
        var inserted = 0
        entities.forEach { entity ->
            require(entity.title.isNotBlank() && entity.amountMinor > 0 && entity.currency == "PHP")
            require(entity.type in listOf("INCOME", "EXPENSE", "TRANSFER") && entity.category.isNotBlank())
            val draft = validated(entity.draft())
            if (known.add(draft)) { upsert(draft); inserted++ }
        }
        return inserted
    }

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun accountById(id: Long): AccountEntity?
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun categoryById(id: Long): CategoryEntity?
    @Query("SELECT * FROM categories WHERE nameKey = :key AND type = :type")
    suspend fun categoryByName(key: String, type: String): CategoryEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategory(category: CategoryEntity): Long

    suspend fun categoryForName(name: String, type: String): CategoryEntity {
        val key = name.trim().lowercase()
        categoryByName(key, type)?.let { return it }
        insertCategory(CategoryEntity(name = name.trim(), nameKey = key, type = type))
        return requireNotNull(categoryByName(key, type))
    }

    suspend fun validated(entity: TransactionEntity): TransactionEntity {
        require(entity.title.isNotBlank() && entity.amountMinor > 0 && entity.currency == "PHP")
        require(entity.type in listOf("INCOME", "EXPENSE", "TRANSFER"))
        require(entity.status in listOf("CONFIRMED", "NEEDS_REVIEW"))
        val previous = if (entity.id != 0L) byId(entity.id) else null
        val account = requireNotNull(accountById(entity.accountId)) { "Choose an account." }
        require(!account.archived || previous?.accountId == account.id) { "Choose an active account." }
        if (entity.type == "TRANSFER") {
            val to = requireNotNull(entity.toAccountId?.let { accountById(it) }) { "Choose a destination account." }
            require(to.id != account.id) { "From and To accounts must differ." }
            require(!to.archived || previous?.toAccountId == to.id)
            return entity.copy(category = "Transfer", categoryId = null)
        }
        require(entity.toAccountId == null)
        val category = if (entity.categoryId != null) requireNotNull(categoryById(entity.categoryId)) { "Choose an existing category." }
            else categoryForName(entity.category, entity.type)
        require(category != null && category.type == entity.type)
        require(!category.archived || previous?.categoryId == category.id) { "Choose an active category." }
        return entity.copy(category = category.name, categoryId = category.id)
    }

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

    /**
     * Saves a row and replaces its pending sync operation when its confirmation state requires one.
     *
     * @throws IllegalArgumentException if the title is blank or the amount is not positive.
     */
    @androidx.room.Transaction
    suspend fun saveLocally(entity: TransactionEntity): Long {
        require(entity.title.isNotBlank() && entity.amountMinor > 0)
        val previous = if (entity.id != 0L) byId(entity.id) else null
        val inserted = upsert(validated(entity))
        val id = if (entity.id == 0L) inserted else entity.id
        if (entity.status == "CONFIRMED") enqueue(PendingChangeEntity(transactionId = id, operation = "UPSERT"))
        else if (previous?.status == "CONFIRMED") enqueue(PendingChangeEntity(transactionId = id, operation = "DELETE"))
        return id
    }

    /** Deletes an existing row and queues deletion only if it was confirmed; missing IDs are ignored. */
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
        WHERE status = 'CONFIRMED' AND currency = 'PHP'
        """
    )
    fun observeConfirmedNetMinor(): Flow<Long>
}
