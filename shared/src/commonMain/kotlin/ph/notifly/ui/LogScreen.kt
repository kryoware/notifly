package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
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
        LogState(rows.filter { it.result != CaptureResult.IGNORED && (f == null || it.result == f) }
            .sortedByDescending { it.capturedAt }, f, keep)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogState())
    init { work { captures.purgeExpired() } }
    fun filter(value: CaptureResult?) { filter.value = value }
    /** Updates retention and permanently redacts stored bodies when retention is disabled. */
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

private fun CaptureResult?.icon() = when (this) {
    null -> Res.drawable.symbol_list
    CaptureResult.PARSED -> Res.drawable.symbol_check
    CaptureResult.NEEDS_REVIEW -> Res.drawable.symbol_pending_actions
    CaptureResult.UNRECOGNIZED -> Res.drawable.symbol_help
    CaptureResult.IGNORED -> Res.drawable.symbol_clear
}

@Composable
fun LogScreen(model: LogModel, appLabels: Map<String, String> = emptyMap()) {
    val s by model.state.collectAsState()
    var expanded by remember { mutableStateOf<Long?>(null) }
    var clear by remember { mutableStateOf(false) }
    val parser = remember { NotificationParser() }
    Column(Modifier.fillMaxSize()) {
        val filters = listOf(null) + CaptureResult.entries.filter { it != CaptureResult.IGNORED }
        SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            filters.forEachIndexed { index, result ->
                SegmentedButton(
                    selected = s.filter == result,
                    onClick = { model.filter(result) },
                    shape = SegmentedButtonDefaults.itemShape(index, filters.size),
                    icon = { Icon(painterResource(result.icon()), contentDescription = null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                    label = { Text(result?.label() ?: "All") },
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingsRow("Keep raw text on device", checked = s.keepRaw, onCheckedChange = model::retain)
            Text("Raw text is never uploaded and is removed after 24 hours.", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTooltip("Export CSV") { IconButton(onClick = {
                val tab = s.filter?.exportName() ?: "all"
                shareCsv(s.captures.toCsv(), "${tab}_${Clock.System.now().epochSeconds}.csv")
            }, enabled = s.captures.isNotEmpty()) {
                Icon(painterResource(Res.drawable.symbol_file_download), contentDescription = "Export CSV")
            } }
            IconTooltip("Clear log") { IconButton(onClick = { clear = true }, enabled = s.captures.isNotEmpty()) {
                Icon(painterResource(Res.drawable.symbol_delete), contentDescription = "Clear log")
            } }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (s.captures.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                Text("Notifications from your allowed apps will show up here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.captures, key = { it.id }) { capture ->
                val color = when (capture.result) {
                    CaptureResult.PARSED -> MaterialTheme.accents.income
                    CaptureResult.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiary
                    CaptureResult.UNRECOGNIZED -> MaterialTheme.colorScheme.error
                    CaptureResult.IGNORED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val isExpanded = expanded == capture.id
                val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                Card(onClick = { expanded = if (isExpanded) null else capture.id },
                    modifier = Modifier.fillMaxWidth().animateItem().semantics {
                        stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                        customActions = listOf(CustomAccessibilityAction(if (isExpanded) "Collapse details" else "Expand details") {
                            expanded = if (isExpanded) null else capture.id
                            true
                        })
                    }) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        val label = appLabels[capture.sourceApp] ?: capture.sourceApp
                        ListItem(
                            leadingContent = { AppIcon(remember(capture.sourceApp, appLabels) { appLabels.packageFor(capture.sourceApp) }, label) },
                            headlineContent = { Text("$label · ${capture.result.label()}", color = color) },
                            trailingContent = { Icon(painterResource(Res.drawable.symbol_expand_more), contentDescription = null, modifier = Modifier.rotate(rotation)) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
                        }, modifier = Modifier.padding(horizontal = 16.dp).animateContentSize(), maxLines = if (isExpanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                        AnimatedVisibility(isExpanded) {
                            val draft = remember(capture.body) { capture.body?.let { (parser.parse(it) as? ParseOutcome.Parsed)?.draft } }
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Amount: ${capture.matchedAmount ?: "Not identified"}")
                                Text("Direction: ${capture.matchedDirection ?: "Not identified"}")
                                Text("Merchant: ${draft?.merchant ?: "Not available"}")
                                Text("Source: $label")
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

private fun CaptureResult.exportName() = when (this) {
    CaptureResult.PARSED -> "parsed"
    CaptureResult.NEEDS_REVIEW -> "needs_review"
    CaptureResult.UNRECOGNIZED -> "unrecognized"
    CaptureResult.IGNORED -> "ignored"
}
