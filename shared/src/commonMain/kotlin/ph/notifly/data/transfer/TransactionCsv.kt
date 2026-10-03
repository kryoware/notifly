package ph.notifly.data.transfer

import ph.notifly.domain.model.*
import kotlin.time.Instant

/** Ledger CSV v3 includes transfer fees; v1/v2 imports remain supported. Raw notification content is excluded. */
object TransactionCsv {
    const val MAX_CHARS = 5_000_000
    const val MAX_ROWS = 10_000
    private val columns = listOf("title", "amount_minor", "currency", "type", "status", "category",
        "occurred_at", "created_at", "source_app", "note", "from_app", "to_app")

    private val accountColumns = listOf("account_name", "account_type", "to_account_name", "to_account_type")
    data class Entry(val transaction: Transaction, val accountName: String? = null, val accountType: AccountType? = null,
        val toAccountName: String? = null, val toAccountType: AccountType? = null)

    fun encode(rows: List<Transaction>, accounts: List<Account> = emptyList()): String = buildString {
        appendLine((columns + accountColumns + "fee_minor").joinToString(","))
        rows.forEach { t ->
            val account = accounts.find { it.id == t.accountId }
            val to = accounts.find { it.id == t.toAccountId }
            appendLine(listOf(t.title, t.amountMinor.toString(), t.currency, t.type.name, t.status.name,
                t.category, Instant.fromEpochMilliseconds(t.occurredAt.toEpochMilliseconds()).toString(),
                Instant.fromEpochMilliseconds(t.createdAt.toEpochMilliseconds()).toString(), t.sourceApp.orEmpty(),
                t.note, t.fromApp.orEmpty(), t.toApp.orEmpty(), account?.name.orEmpty(), account?.type?.name.orEmpty(),
                to?.name.orEmpty(), to?.type?.name.orEmpty(), t.feeMinor.toString()).joinToString(",") { value ->
                // Prevent spreadsheet formulas; doubling an existing apostrophe keeps this reversible.
                val safe = if (value.needsSpreadsheetEscape()) "'$value" else value
                "\"${safe.replace("\"", "\"\"")}\""
            })
        }
    }

    /** Validates the entire file before returning drafts; file status never confirms an import. */
    fun decode(csv: String): List<Transaction> = decodeEntries(csv).map { it.transaction }

    fun decodeEntries(csv: String): List<Entry> {
        require(csv.length <= MAX_CHARS) { "CSV is too large. Use a file under 5 million characters." }
        val records = records(csv.removePrefix("\uFEFF"))
        val headers = records.firstOrNull()
        require(headers == columns || headers == columns + accountColumns || headers == columns + accountColumns + "fee_minor") {
            "Choose a Notifly transaction CSV with the original column headers." }
        return records.drop(1).mapIndexed { index, fields ->
            val message = "Invalid transaction at CSV row ${index + 2}. Check its fields and try again."
            require(fields.size == headers?.size) { message }
            val f = fields.map { value ->
                if (value.startsWith("'") && value.drop(1).needsSpreadsheetEscape()) value.drop(1) else value
            }
            try {
                val amount = f[1].toLongOrNull()
                val fee = if (f.size == 17) requireNotNull(f[16].toLongOrNull()) else 0L
                require(f[0].isNotBlank() && amount != null && amount > 0 && f[2] == "PHP" && f[5].isNotBlank())
                require(fee >= 0 && amount <= Long.MAX_VALUE - fee && (f[3] == "TRANSFER" || fee == 0L))
                TransactionStatus.valueOf(f[4])
                val occurred = Instant.parse(f[6])
                val created = Instant.parse(f[7])
                // The database stores milliseconds; reject dates outside that representation.
                require(Instant.fromEpochMilliseconds(occurred.toEpochMilliseconds()) == occurred)
                require(Instant.fromEpochMilliseconds(created.toEpochMilliseconds()) == created)
                val transaction = Transaction(title = f[0], amountMinor = amount, currency = f[2], type = TransactionType.valueOf(f[3]),
                    status = TransactionStatus.NEEDS_REVIEW, category = f[5], occurredAt = occurred, createdAt = created,
                    sourceApp = f[8].ifEmpty { null }, captureId = null, note = f[9],
                    fromApp = f[10].ifEmpty { null }, toApp = f[11].ifEmpty { null }, accountId = 0, feeMinor = fee)
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
            require(rows.size <= MAX_ROWS + 1) { "Import up to 10,000 transactions at a time." }
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
            require(fields.size < columns.size + accountColumns.size + 1) { "Too many CSV columns." }
        }
        require(!quoted) { "CSV has an unclosed quoted field." }
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) endRow()
        return rows
    }
}
