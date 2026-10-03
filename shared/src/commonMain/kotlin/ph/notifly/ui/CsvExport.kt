package ph.notifly.ui

import androidx.compose.runtime.Composable

/** Returns a launcher that asks where to save the CSV built by [csv]; unsupported platforms do nothing. */
@Composable
expect fun rememberCsvSaver(csv: () -> String, onMessage: (String) -> Unit): (filename: String) -> Unit

internal fun requireNotificationExport(debuggable: Boolean) {
    if (!debuggable) throw IllegalStateException("Log export requires a debuggable build")
}
