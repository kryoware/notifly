package ph.notifly.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.LedgerRepository
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ph.notifly.domain.repository.TransactionRepository

data class TransactionFiles(val available: Boolean, val busy: Boolean = false,
    val import: () -> Unit = {}, val export: () -> Unit = {})

@Composable
expect fun rememberTransactionFiles(
    onImport: suspend (String) -> Unit,
    exportCsv: suspend () -> String,
    onMessage: suspend (String) -> Unit,
): TransactionFiles

@Composable
internal fun TransactionDataControls(repository: TransactionRepository, demo: Boolean, ledger: LedgerRepository? = null, onMessage: suspend (String) -> Unit) {
    var preview by remember { mutableStateOf<List<TransactionCsv.Entry>?>(null) }
    val accounts by (ledger?.observeAccounts() ?: kotlinx.coroutines.flow.flowOf(emptyList<Account>())).collectAsState(emptyList())
    var mapping by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    fun key(entry: TransactionCsv.Entry, destination: Boolean): String {
        val name = if (destination) entry.toAccountName else entry.accountName
        val type = if (destination) entry.toAccountType else entry.accountType
        val pkg = if (destination) entry.transaction.toApp else entry.transaction.fromApp ?: entry.transaction.sourceApp
        val role = if (destination) "To" else if (entry.transaction.type == TransactionType.TRANSFER) "From" else "Account"
        val identity = if (name != null) "$name · ${type?.name.orEmpty()}" else pkg ?: "Manual / unassigned"
        return "$role: $identity"
    }
    fun suggested(entry: TransactionCsv.Entry, destination: Boolean): Long? {
        val name = if (destination) entry.toAccountName else entry.accountName
        val type = if (destination) entry.toAccountType else entry.accountType
        val pkg = if (destination) entry.transaction.toApp else entry.transaction.fromApp ?: entry.transaction.sourceApp
        return accounts.filter { !it.archived && if (name != null) it.name.equals(name, true) && it.type == type else pkg != null && pkg in it.linkedApps }.singleOrNull()?.id
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val files = rememberTransactionFiles(
        onImport = { csv ->
            try {
                val rows = withContext(Dispatchers.Default) { TransactionCsv.decodeEntries(csv) }
                if (rows.isEmpty()) onMessage("This CSV has no transactions.") else { mapping = emptyMap(); preview = rows }
            } catch (e: IllegalArgumentException) { onMessage(e.message ?: "Invalid transaction CSV.") }
        },
        exportCsv = { withContext(Dispatchers.Default) { TransactionCsv.encode(repository.observeAll().first(), accounts) } },
        onMessage = onMessage,
    )
    val enabled = files.available && !files.busy && !saving && !demo && preview == null
    val unavailable = when {
        demo -> "Switch off demo mode to import or export your transactions"
        !files.available -> "File import and export are available on Android"
        files.busy || saving -> "Working…"
        else -> null
    }
    SettingsRow("Import transactions", unavailable ?: "Choose a Notifly CSV. Imported rows wait for your review.",
        onClick = if (enabled) files.import else null)
    SettingsRow("Export transactions", unavailable ?: "Save all transactions as CSV, without notification text",
        onClick = if (enabled) files.export else null)
    preview?.let { entries ->
        val endpoints = entries.flatMap { entry -> listOf(entry to false) + if (entry.transaction.type == TransactionType.TRANSFER) listOf(entry to true) else emptyList() }.distinctBy { (entry, destination) -> key(entry, destination) }
        val assignments = endpoints.associate { (entry, destination) -> key(entry, destination) to (mapping[key(entry, destination)] ?: suggested(entry, destination)) }
        val valid = entries.all { entry ->
            val from = assignments[key(entry, false)]
            val to = assignments[key(entry, true)]
            from != null && (entry.transaction.type != TransactionType.TRANSFER || (to != null && from != to))
        }
        AlertDialog(
            onDismissRequest = { if (!saving) preview = null },
            title = { Text("Import ${entries.size} transactions?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Assign accounts before importing. Rows await review; exact duplicates are skipped. Categories missing from your list will be added.")
                if (accounts.none { !it.archived }) Text("Create an account in Settings → Accounts first.")
                endpoints.forEach { (entry, destination) ->
                    val identifier = key(entry, destination)
                    AccountPicker(identifier, assignments[identifier], accounts.filter { !it.archived }) { mapping = mapping + (identifier to it) }
                }
                if (!valid) Text("Choose accounts, with different From and To accounts for each transfer.")
            } },
            confirmButton = { TextButton(enabled = !saving && valid, onClick = {
                saving = true
                scope.launch {
                    try {
                        val rows = entries.map { entry -> entry.transaction.copy(accountId = requireNotNull(assignments[key(entry, false)]),
                            toAccountId = if (entry.transaction.type == TransactionType.TRANSFER) requireNotNull(assignments[key(entry, true)]) else null) }
                        val count = repository.importTransactions(rows)
                        preview = null
                        onMessage("$count transactions imported for review. ${rows.size - count} duplicates skipped.")
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { onMessage("Couldn't import transactions. No rows were added. Please try again.") }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Importing…" else "Import") } },
            dismissButton = { TextButton(enabled = !saving, onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}
