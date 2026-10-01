package ph.notifly.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTransactionFiles(
    onImport: suspend (String) -> Unit,
    exportCsv: suspend () -> String,
    onMessage: suspend (String) -> Unit,
): TransactionFiles = TransactionFiles(available = false)
