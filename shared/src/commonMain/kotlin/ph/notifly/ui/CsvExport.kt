package ph.notifly.ui

import androidx.compose.runtime.Composable

/** Returns a launcher that asks where to save the CSV built by [csv]; unsupported platforms do nothing. */
@Composable
expect fun rememberCsvSaver(csv: () -> String): (filename: String) -> Unit
