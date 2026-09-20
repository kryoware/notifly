package ph.notifly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import ph.notifly.data.local.AppPreferences
import ph.notifly.data.parser.NotificationParser
import ph.notifly.data.parser.ParseOutcome
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.CaptureRepository
import ph.notifly.ui.theme.accents

data class LogState(val captures: List<RawCapture> = emptyList(), val filter: CaptureResult? = null, val keepRaw: Boolean = false)
class LogModel(private val captures: CaptureRepository, private val preferences: AppPreferences) : ScreenModel() {
    private val filter = MutableStateFlow<CaptureResult?>(null)
    val state = combine(captures.observeLog(), filter, preferences.keepRawText) { rows, f, keep ->
        LogState(rows.filter { f == null || it.result == f }.sortedByDescending { it.capturedAt }, f, keep)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogState())
    init { work { captures.purgeExpired() } }
    fun filter(value: CaptureResult?) { filter.value = value }
    fun retain(value: Boolean) = work {
        preferences.setKeepRawText(value)
        if (!value) captures.redactBodies()
    }
    fun clear() = work { captures.clearLog() }
}

private fun CaptureResult.label() = when (this) {
    CaptureResult.PARSED -> "Parsed"
    CaptureResult.NEEDS_REVIEW -> "Needs review"
    CaptureResult.UNRECOGNIZED -> "Not recognised"
    CaptureResult.IGNORED -> "Ignored"
}

@Composable
fun LogScreen(model: LogModel) {
    val s by model.state.collectAsState()
    var expanded by remember { mutableStateOf<Long?>(null) }
    var clear by remember { mutableStateOf(false) }
    val parser = remember { NotificationParser() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Keep raw text on device", Modifier.weight(1f))
            Switch(s.keepRaw, model::retain, modifier = Modifier.semantics { contentDescription = "Keep raw text on device" })
        }
        Text("Raw text is never uploaded and is removed after 24 hours.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.filter == null, { model.filter(null) }, label = { Text("All") })
            CaptureResult.entries.forEach { result -> FilterChip(s.filter == result, { model.filter(result) }, label = { Text(result.label()) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { shareCsv(s.captures.toCsv()) }, enabled = s.captures.isNotEmpty()) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
            }
            IconButton(onClick = { clear = true }, enabled = s.captures.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = "Clear log")
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.captures.isEmpty()) item { Text("No captures to show.") }
            items(s.captures, key = { it.id }) { capture ->
                val color = when (capture.result) {
                    CaptureResult.PARSED -> MaterialTheme.accents.income
                    CaptureResult.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiary
                    CaptureResult.UNRECOGNIZED -> MaterialTheme.colorScheme.error
                    CaptureResult.IGNORED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val isExpanded = expanded == capture.id
                val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                Card(Modifier.fillMaxWidth().animateItem()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        ListItem(
                            modifier = Modifier.clickable { expanded = if (isExpanded) null else capture.id },
                            headlineContent = { Text("${capture.sourceApp} · ${capture.result.label()}", color = color) },
                            trailingContent = { Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation)) },
                        )
                        val highlight = MaterialTheme.colorScheme.tertiaryContainer
                        val onHighlight = MaterialTheme.colorScheme.onTertiaryContainer
                        Text(buildAnnotatedString {
                            val body = capture.body ?: "Raw text not retained"
                            append(body)
                            listOfNotNull(capture.matchedAmount, capture.matchedDirection).filter { it.isNotEmpty() }.forEach { match ->
                                val start = body.indexOf(match, ignoreCase = true)
                                if (start >= 0) addStyle(SpanStyle(background = highlight, color = onHighlight, fontWeight = FontWeight.Bold), start, start + match.length)
                            }
                        }, modifier = Modifier.padding(horizontal = 16.dp))
                        AnimatedVisibility(isExpanded) {
                            val draft = remember(capture.body) { capture.body?.let { (parser.parse(it) as? ParseOutcome.Parsed)?.draft } }
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Amount: ${capture.matchedAmount ?: "Not identified"}")
                                Text("Direction: ${capture.matchedDirection ?: "Not identified"}")
                                Text("Merchant: ${draft?.merchant ?: "Not available"}")
                                Text("Source: ${capture.sourceApp}")
                                Text("Captured: ${capture.capturedAt}")
                                Text(capture.reason)
                                if (capture.result == CaptureResult.UNRECOGNIZED) OutlinedButton(onClick = { model.navigate("from-log/${capture.id}") }) { Text("Create transaction manually") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear notification log?") },
        text = { Text("This removes the local log. Your transactions remain.") },
        confirmButton = { TextButton(onClick = { clear = false; model.clear() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
