package ph.notifly.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.Transaction
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
internal fun TransactionDataControls(repository: TransactionRepository, demo: Boolean, onMessage: suspend (String) -> Unit) {
    var preview by remember { mutableStateOf<List<Transaction>?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val files = rememberTransactionFiles(
        onImport = { csv ->
            try {
                val rows = withContext(Dispatchers.Default) { TransactionCsv.decode(csv) }
                if (rows.isEmpty()) onMessage("This CSV has no transactions.") else preview = rows
            } catch (e: IllegalArgumentException) { onMessage(e.message ?: "Invalid transaction CSV.") }
        },
        exportCsv = { withContext(Dispatchers.Default) { TransactionCsv.encode(repository.observeAll().first()) } },
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
    preview?.let { rows ->
        AlertDialog(
            onDismissRequest = { if (!saving) preview = null },
            title = { Text("Import ${rows.size} transactions?") },
            text = { Text("These transactions will be added to Needs review. Your balance stays unchanged until you confirm them. Exact duplicates are skipped; existing transactions stay unchanged.") },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try {
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
