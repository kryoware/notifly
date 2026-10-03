package ph.notifly.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberCsvSaver(csv: () -> String, onMessage: (String) -> Unit): (filename: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentCsv by rememberUpdatedState(csv)
    val message by rememberUpdatedState(onMessage)
    fun requireDebug() = requireNotificationExport(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                requireDebug()
                val text = currentCsv()
                withContext(Dispatchers.IO) {
                    requireDebug()
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                message("Log exported.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Couldn't export log. Export again to retry.") }
        }
    }
    return { filename ->
        try { requireDebug(); saver.launch(filename) }
        catch (_: IllegalStateException) { message("Log export is available only in debug builds.") }
        catch (_: Exception) { message("No file picker is available on this device.") }
    }
}
