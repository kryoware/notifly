package ph.notifly.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberCsvSaver(csv: () -> String): (filename: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentCsv by rememberUpdatedState(csv)
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = currentCsv()
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                toast("Log exported.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { toast("Couldn't export log. Export again to retry.") }
        }
    }
    return { filename ->
        try { saver.launch(filename) }
        catch (_: Exception) { toast("No file picker is available on this device.") }
    }
}
