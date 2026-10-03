package ph.notifly.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.notifly.data.transfer.TransactionCsv
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import kotlin.time.Clock

@Composable
actual fun rememberTransactionFiles(
    onImport: suspend (String) -> Unit,
    exportCsv: suspend () -> String,
    onMessage: suspend (String) -> Unit,
): TransactionFiles {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val currentImport by rememberUpdatedState(onImport)
    val currentExport by rememberUpdatedState(exportCsv)
    val message by rememberUpdatedState(onMessage)
    var busy by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) busy = false
        if (uri != null) scope.launch {
            busy = true
            try {
                val csv = withContext(Dispatchers.IO) {
                    val input = checkNotNull(resolver.openInputStream(uri))
                    InputStreamReader(input, Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)).use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(result.length + count <= TransactionCsv.MAX_CHARS) { "CSV is too large. Use a file under 5 million characters." }
                            result.append(buffer, 0, count)
                        }
                        result.toString()
                    }
                }
                currentImport(csv)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Couldn't read this CSV. Check that it is a UTF-8 Notifly or Budge transaction export under 5 million characters.") }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) busy = false
        if (uri != null) scope.launch {
            busy = true
            try {
                val csv = currentExport()
                withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                }
                message("Transactions exported.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message("Couldn't export transactions. The file may be incomplete; export again to retry.") }
            finally { busy = false }
        }
    }
    return TransactionFiles(available = true, busy = busy,
        import = {
            busy = true
            try { importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }
            catch (_: Exception) { busy = false; scope.launch { message("No file picker is available on this device.") } }
        },
        export = {
            busy = true
            try { exporter.launch("notifly_transactions_${Clock.System.now().epochSeconds}.csv") }
            catch (_: Exception) { busy = false; scope.launch { message("No file picker is available on this device.") } }
        })
}
