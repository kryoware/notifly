package ph.notifly.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.*
import kotlin.time.Instant

@Entity(tableName = "accounts", indices = [Index(value = ["nameKey"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val nameKey: String,
    val type: String, val cardType: String?, val lastFour: String?, val freeTransfer: Boolean,
    val balanceMinor: Long, val balanceAsOfMillis: Long, val dueDate: Int?, val statementDate: Int?,
    val archived: Boolean = false, val legacyPackage: String? = null, val preferencesImported: Boolean = false,
)

@Entity(tableName = "account_apps", primaryKeys = ["accountId", "packageName"], foreignKeys = [
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)])
data class AccountAppEntity(val accountId: Long, val packageName: String)

@Entity(tableName = "categories", indices = [Index(value = ["type", "nameKey"], unique = true)])
data class CategoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val nameKey: String, val type: String, val archived: Boolean = false,
    val budgetMinor: Long? = null, val preferencesImported: Boolean = false)

// Structured drafts deliberately outlive the optional 24-hour raw capture log.
@Entity(tableName = "captured_drafts")
data class CapturedDraftEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: Long?, val title: String, val amountMinor: Long, val type: String,
    val inbound: Boolean?, val occurredAtMillis: Long, val sourceApp: String,
    val accountId: Long?, val toAccountId: Long?, val accountHint: String?)

fun AccountEntity.toDomain(links: List<AccountAppEntity>) = Account(id, name, AccountType.valueOf(type),
    cardType, lastFour, freeTransfer, balanceMinor, Instant.fromEpochMilliseconds(balanceAsOfMillis),
    dueDate, statementDate, archived, links.filter { it.accountId == id }.map { it.packageName }.toSet())
fun CategoryEntity.toDomain() = Category(id, name, TransactionType.valueOf(type), archived, budgetMinor)
fun CapturedDraftEntity.toDomain() = CapturedDraft(id, captureId, title, amountMinor,
    TransactionType.valueOf(type), inbound, Instant.fromEpochMilliseconds(occurredAtMillis), sourceApp,
    accountId, toAccountId, accountHint)
fun CapturedDraft.toEntity() = CapturedDraftEntity(id, captureId, title, amountMinor, type.name,
    inbound, occurredAt.toEpochMilliseconds(), sourceApp, accountId, toAccountId, accountHint)

@Dao
interface LedgerDao : TransactionDao {
    @Query("SELECT * FROM accounts ORDER BY archived, name COLLATE NOCASE")
    fun observeAccounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM account_apps") fun observeLinks(): Flow<List<AccountAppEntity>>
    @Query("SELECT * FROM account_apps") suspend fun links(): List<AccountAppEntity>
    @Query("SELECT * FROM categories ORDER BY archived, id") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM captured_drafts ORDER BY occurredAtMillis DESC") fun observeDrafts(): Flow<List<CapturedDraftEntity>>
    @Query("SELECT * FROM captured_drafts") suspend fun drafts(): List<CapturedDraftEntity>
    @Query("SELECT * FROM captured_drafts WHERE id = :id") suspend fun draft(id: Long): CapturedDraftEntity?
    @Upsert suspend fun upsertAccount(account: AccountEntity): Long
    @Upsert suspend fun upsertCategory(category: CategoryEntity): Long
    @Insert suspend fun insertLinks(links: List<AccountAppEntity>)
    @Query("DELETE FROM account_apps WHERE accountId = :id") suspend fun clearLinks(id: Long)
    @Query("SELECT EXISTS(SELECT 1 FROM allowed_apps WHERE packageName = :pkg AND finance = 1)") suspend fun financeApp(pkg: String): Boolean
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id") suspend fun references(id: Long): Int
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun removeAccount(id: Long)
    @Query("DELETE FROM captured_drafts WHERE id = :id") suspend fun deleteDraft(id: Long)
    @Insert suspend fun insertDraft(draft: CapturedDraftEntity): Long
    @Query("UPDATE transactions SET category = :name WHERE categoryId = :id") suspend fun renameTransactions(id: Long, name: String)
    @Query("INSERT OR REPLACE INTO pending_changes(transactionId, operation) SELECT id, 'UPSERT' FROM transactions WHERE categoryId = :id AND status = 'CONFIRMED' AND currency = 'PHP'")
    suspend fun enqueueCategoryRename(id: Long)

    @androidx.room.Transaction
    suspend fun saveAccount(account: Account): Long {
        account.validate()
        val previous = accountById(account.id)
        require(previous == null || references(account.id) == 0 ||
            (previous.type == "CARD") == (account.type == AccountType.CARD)) { "An account with transactions cannot change between assets and card debt." }
        account.linkedApps.forEach { pkg ->
            require(financeApp(pkg) || links().any { it.accountId == account.id && it.packageName == pkg }) { "Choose a finance app to link." }
        }
        val inserted = upsertAccount(AccountEntity(account.id, account.name.trim(), account.name.trim().lowercase(),
            account.type.name, account.cardType?.trim()?.takeIf { it.isNotEmpty() }, account.lastFour,
            account.freeTransfer, account.balanceMinor, account.balanceAsOf.toEpochMilliseconds(), account.dueDate,
            account.statementDate, account.archived, previous?.legacyPackage, previous?.preferencesImported ?: true))
        val id = account.id.takeIf { it != 0L } ?: inserted
        clearLinks(id)
        insertLinks(account.linkedApps.map { AccountAppEntity(id, it) })
        return id
    }

    @androidx.room.Transaction
    suspend fun deleteAccount(id: Long) {
        require(references(id) == 0 && drafts().none { it.accountId == id || it.toAccountId == id }) { "Archive accounts that have transactions or drafts." }
        clearLinks(id)
        removeAccount(id)
    }

    @androidx.room.Transaction
    suspend fun saveCategory(category: Category): Long {
        require(category.name.isNotBlank() && category.type != TransactionType.TRANSFER)
        require(category.budgetMinor == null || (category.type == TransactionType.EXPENSE && category.budgetMinor > 0))
        val previous = categoryById(category.id)
        require(previous == null || previous.type == category.type.name)
        require(previous?.name != "Other" || (category.name == "Other" && !category.archived)) { "Keep Other available." }
        val name = category.name.trim()
        val inserted = upsertCategory(CategoryEntity(category.id, name, name.lowercase(), category.type.name,
            category.archived, category.budgetMinor, previous?.preferencesImported ?: true))
        val id = category.id.takeIf { it != 0L } ?: inserted
        renameTransactions(id, name)
        if (previous?.name != null && previous.name != name) enqueueCategoryRename(id)
        return id
    }

    @androidx.room.Transaction
    suspend fun confirmDraft(id: Long, entity: TransactionEntity): Long {
        val draft = draft(id) ?: error("Draft no longer exists.")
        require(entity.id == 0L && entity.status == "CONFIRMED")
        val saved = saveLocally(entity.copy(captureId = draft.captureId))
        deleteDraft(id)
        return saved
    }

    @androidx.room.Transaction
    suspend fun importPreferences(balances: Map<String, ManualBalance>, budgets: Map<String, Long>) {
        balances.forEach { (pkg, balance) ->
            val existing = accounts().find { it.legacyPackage == pkg }
            if (existing == null) {
                val id = upsertAccount(AccountEntity(name = pkg, nameKey = pkg.lowercase(), type = "WALLET",
                    cardType = null, lastFour = null, freeTransfer = false, balanceMinor = balance.minor,
                    balanceAsOfMillis = balance.setAt.toEpochMilliseconds(), dueDate = null, statementDate = null,
                    legacyPackage = pkg, preferencesImported = true))
                insertLinks(listOf(AccountAppEntity(id, pkg)))
            } else if (!existing.preferencesImported) {
                upsertAccount(existing.copy(balanceMinor = balance.minor,
                    balanceAsOfMillis = balance.setAt.toEpochMilliseconds(), preferencesImported = true))
            }
        }
        budgets.forEach { (name, budget) ->
            val category = categoryForName(name, "EXPENSE")
            if (!category.preferencesImported) upsertCategory(category.copy(budgetMinor = budget, preferencesImported = true))
        }
        seedCategories()
    }

    @androidx.room.Transaction
    suspend fun seedCategories() {
        DEFAULT_EXPENSE_CATEGORIES.forEach { categoryForName(it, "EXPENSE") }
        DEFAULT_INCOME_CATEGORIES.forEach { categoryForName(it, "INCOME") }
    }
}
