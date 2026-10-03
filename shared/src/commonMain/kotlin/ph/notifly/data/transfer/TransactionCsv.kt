package ph.notifly.data.transfer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import ph.notifly.domain.model.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Ledger CSV v2 with portable account names/types; v1 imports remain supported. Budge exports are also accepted. IDs and raw notification content are deliberately excluded. */
object TransactionCsv {
    const val MAX_CHARS = 5_000_000
    const val MAX_ROWS = 10_000
    private val columns = listOf("title", "amount_minor", "currency", "type", "status", "category",
        "occurred_at", "created_at", "source_app", "note", "from_app", "to_app")

    private val accountColumns = listOf("account_name", "account_type", "to_account_name", "to_account_type")
    data class Entry(val transaction: Transaction, val accountName: String? = null, val accountType: AccountType? = null,
        val toAccountName: String? = null, val toAccountType: AccountType? = null)

    fun encode(rows: List<Transaction>, accounts: List<Account> = emptyList()): String = buildString {
        appendLine((columns + accountColumns).joinToString(","))
        rows.forEach { t ->
            val account = accounts.find { it.id == t.accountId }
            val to = accounts.find { it.id == t.toAccountId }
            appendLine(listOf(t.title, t.amountMinor.toString(), t.currency, t.type.name, t.status.name,
                t.category, Instant.fromEpochMilliseconds(t.occurredAt.toEpochMilliseconds()).toString(),
                Instant.fromEpochMilliseconds(t.createdAt.toEpochMilliseconds()).toString(), t.sourceApp.orEmpty(),
                t.note, t.fromApp.orEmpty(), t.toApp.orEmpty(), account?.name.orEmpty(), account?.type?.name.orEmpty(),
                to?.name.orEmpty(), to?.type?.name.orEmpty()).joinToString(",") { value ->
                // Prevent spreadsheet formulas; doubling an existing apostrophe keeps this reversible.
                val safe = if (value.needsSpreadsheetEscape()) "'$value" else value
                "\"${safe.replace("\"", "\"\"")}\""
            })
        }
    }

    /** Validates the entire file before returning drafts; file status never confirms an import. */
    fun decode(csv: String): List<Transaction> = decodeEntries(csv).map { it.transaction }

    fun decodeEntries(csv: String, zone: TimeZone = TimeZone.currentSystemDefault()): List<Entry> {
        require(csv.length <= MAX_CHARS) { "CSV is too large. Use a file under 5 million characters." }
        val records = records(csv.removePrefix("\uFEFF"))
        val headers = records.firstOrNull()
        if (headers == budgeMeta) return decodeBudge(records, zone)
        require(headers == columns || headers == columns + accountColumns) { HEADER_ERROR }
        require(records.size <= MAX_ROWS + 1) { TOO_MANY_ROWS }
        return records.drop(1).mapIndexed { index, fields ->
            val message = "Invalid transaction at CSV row ${index + 2}. Check its fields and try again."
            require(fields.size == headers?.size) { message }
            val f = fields.map { value ->
                if (value.startsWith("'") && value.drop(1).needsSpreadsheetEscape()) value.drop(1) else value
            }
            try {
                val amount = f[1].toLongOrNull()
                require(f[0].isNotBlank() && amount != null && amount > 0 && f[2] == "PHP" && f[5].isNotBlank())
                TransactionStatus.valueOf(f[4])
                val occurred = Instant.parse(f[6])
                val created = Instant.parse(f[7])
                // The database stores milliseconds; reject dates outside that representation.
                require(Instant.fromEpochMilliseconds(occurred.toEpochMilliseconds()) == occurred)
                require(Instant.fromEpochMilliseconds(created.toEpochMilliseconds()) == created)
                val transaction = Transaction(title = f[0], amountMinor = amount, currency = f[2], type = TransactionType.valueOf(f[3]),
                    status = TransactionStatus.NEEDS_REVIEW, category = f[5], occurredAt = occurred, createdAt = created,
                    sourceApp = f[8].ifEmpty { null }, captureId = null, note = f[9],
                    fromApp = f[10].ifEmpty { null }, toApp = f[11].ifEmpty { null }, accountId = 0)
                if (f.size == columns.size) Entry(transaction) else {
                    val accountName = f[12].takeIf { it.isNotBlank() }
                    val accountType = f[13].takeIf { it.isNotBlank() }?.let(AccountType::valueOf)
                    val toName = f[14].takeIf { it.isNotBlank() }
                    val toType = f[15].takeIf { it.isNotBlank() }?.let(AccountType::valueOf)
                    require((accountName == null) == (accountType == null))
                    require((toName == null) == (toType == null))
                    Entry(transaction, accountName, accountType, toName, toType)
                }
            } catch (_: IllegalArgumentException) { throw IllegalArgumentException(message) }
        }
    }

    private const val TOO_MANY_ROWS = "Import up to 10,000 transactions at a time."
    private const val SECTION_ROWS = 1_000
    private const val HEADER_ERROR = "Choose a Notifly or Budge transaction CSV with the original column headers."
    private val budgeMeta = listOf("Format version", "Period", "User ID")
    private val budgeColumns = listOf("Date", "Payment", "Is paid", "Amount", "Currency", "Account", "Category",
        "Subcategory", "Goal", "Description")
    private val budgeAccountColumns = listOf("Account", "Account Balance", "Available Balance", "Credit Limit",
        "Currency", "Is savings", "Description")
    private val budgeAmount = Regex("""(-?)(\d{1,15})(?:\.(\d{1,2}))?""")

    /** Budge exports are `###`-separated sections: metadata, transactions, accounts, goals. Goals are ignored. */
    private fun decodeBudge(records: List<List<String>>, zone: TimeZone): List<Entry> {
        val sections = mutableListOf(mutableListOf<List<String>>())
        records.forEach { if (it == listOf("###")) sections.add(mutableListOf()) else sections.last().add(it) }
        require(sections[0].getOrNull(1)?.firstOrNull() == "1" && sections[0].size == 2) { "Unsupported Budge export version." }
        var rows: List<List<String>>? = null
        var hasAccounts = false
        sections.drop(1).forEach { section ->
            when (section.firstOrNull()?.firstOrNull()) {
                null, "Goal" -> Unit
                "Date" -> { require(section[0] == budgeColumns && rows == null) { HEADER_ERROR }; rows = section.drop(1) }
                "Account" -> { require(section[0] == budgeAccountColumns && !hasAccounts) { HEADER_ERROR }; hasAccounts = true }
                else -> throw IllegalArgumentException(HEADER_ERROR)
            }
        }
        // A missing accounts section means the export was truncated.
        require(hasAccounts) { HEADER_ERROR }
        val transactionRows = requireNotNull(rows) { HEADER_ERROR }
        require(transactionRows.size <= MAX_ROWS) { TOO_MANY_ROWS }
        val entries = transactionRows.mapIndexedNotNull { index, f ->
            val message = "Invalid Budge transaction #${index + 1}. Check its fields and try again."
            require(f.size == budgeColumns.size) { message }
            try {
                // Unpaid rows are planned payments that have not happened yet.
                if (!f[2].toBooleanStrict()) return@mapIndexedNotNull null
                val (day, month, year) = f[0].split("-").also { require(it.size == 3) }.map { it.toInt() }
                val amount = requireNotNull(budgeAmount.matchEntire(f[3])).groupValues
                val minor = amount[2].toLong() * 100 + amount[3].padEnd(2, '0').toLong()
                require(minor > 0 && f[4] == "PHP")
                val occurred = LocalDate(year, month, day).atStartOfDayIn(zone)
                val category = f[6].trim().ifEmpty { "Other" }
                Entry(Transaction(title = f[1].trim().ifEmpty { category }, amountMinor = minor,
                    type = if (amount[1] == "-") TransactionType.EXPENSE else TransactionType.INCOME,
                    status = TransactionStatus.NEEDS_REVIEW, category = category, occurredAt = occurred,
                    sourceApp = null, captureId = null,
                    note = listOf(f[7], f[9]).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · "),
                    accountId = 0), accountName = f[5].trim().ifEmpty { null })
            } catch (_: IllegalArgumentException) { throw IllegalArgumentException(message) }
        }
        // Budge dates have no time, so rows identical after import get distinct createdAt values to survive import dedupe.
        val seen = mutableMapOf<Entry, Int>()
        return entries.map { entry ->
            val repeat = seen[entry] ?: 0
            seen[entry] = repeat + 1
            val t = entry.transaction
            entry.copy(transaction = t.copy(createdAt = t.occurredAt + repeat.milliseconds))
        }
    }

    private fun String.needsSpreadsheetEscape(): Boolean =
        firstOrNull()?.let { it in "'=+-@\t\r\n" } == true ||
            trimStart().firstOrNull()?.let { it in "=+-@" } == true

    private fun records(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun endField() { fields.add(field.toString()); field.clear(); closed = false }
        fun endRow() {
            endField()
            if (fields != listOf("")) rows.add(fields.toList())
            fields.clear()
            // Budge adds section rows around its transactions; each path enforces MAX_ROWS on transactions only.
            require(rows.size <= MAX_ROWS + 1 + SECTION_ROWS) { TOO_MANY_ROWS }
        }
        while (i < csv.length) {
            val c = csv[i++]
            if (quoted) {
                if (c == '"') {
                    if (csv.getOrNull(i) == '"') { field.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closed) { "Invalid CSV quoting." }; quoted = true }
                ',' -> endField()
                '\r', '\n' -> { if (c == '\r' && csv.getOrNull(i) == '\n') i++; endRow() }
                else -> { require(!closed) { "Invalid CSV quoting." }; field.append(c) }
            }
            require(fields.size < columns.size + accountColumns.size) { "Too many CSV columns." }
        }
        require(!quoted) { "CSV has an unclosed quoted field." }
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) endRow()
        return rows
    }
}
