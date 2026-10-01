package ph.notifly.ui

import androidx.compose.runtime.Composable

/** Saving CSV files is unavailable on iOS; the launcher has no effect. */
@Composable
actual fun rememberCsvSaver(csv: () -> String): (filename: String) -> Unit = {}
