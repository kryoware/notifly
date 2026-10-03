package ph.notifly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.symbol_backspace
import org.jetbrains.compose.resources.painterResource
import ph.notifly.data.local.PIN_LENGTH
import ph.notifly.data.local.PinResult
import ph.notifly.ui.theme.Space

private val digitsOnly = InputTransformation {
    if (length > PIN_LENGTH || !asCharSequence().all(Char::isDigit)) revertAllChanges()
}

@Composable
private fun PinField(state: TextFieldState, label: String, isError: Boolean = false, error: String? = null, onDone: () -> Unit = {}) {
    OutlinedSecureTextField(state, label = { Text(label) }, inputTransformation = digitsOnly,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() }, isError = isError || error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } })
}

/** Asks for the current PIN before removing it. */
@Composable
internal fun PinDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val pin = rememberTextFieldState()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Remove app PIN") },
        text = { PinField(pin, "Current PIN") },
        confirmButton = { TextButton(onClick = { onDone(pin.text.toString()) }, enabled = pin.text.length == PIN_LENGTH) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private val keyCell = Modifier.size(width = 88.dp, height = 72.dp)

@Composable
private fun PinKey(digit: Char, onType: (Char) -> Unit) = Box(keyCell, contentAlignment = Alignment.Center) {
    TextButton(onClick = { onType(digit) }, Modifier.size(72.dp), shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun PinDots(entered: Int) {
    Row(Modifier.padding(vertical = Space.lg).clearAndSetSemantics { contentDescription = "$entered of $PIN_LENGTH digits entered" },
        horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
        repeat(PIN_LENGTH) { i ->
            Box(Modifier.size(16.dp).background(
                if (i < entered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape))
        }
    }
}

@Composable
private fun PinKeypad(entry: String, onType: (Char) -> Unit, onDelete: () -> Unit, leading: @Composable () -> Unit) {
    for (row in listOf("123", "456", "789")) Row { row.forEach { PinKey(it, onType) } }
    Row {
        Box(keyCell, contentAlignment = Alignment.Center) { leading() }
        PinKey('0', onType)
        Box(keyCell, contentAlignment = Alignment.Center) {
            IconTooltip("Delete digit") { IconButton(onClick = onDelete, enabled = entry.isNotEmpty()) {
                Icon(painterResource(Res.drawable.symbol_backspace), "Delete digit", modifier = mirroredIconModifier())
            } }
        }
    }
}

/** Full-screen keypad that asks for a new PIN twice and calls [onDone] once both entries match. */
@Composable
internal fun PinSetupScreen(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    // Plain remember, not rememberSaveable: a half-typed PIN must not land in the saved-state bundle.
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry) {
        if (entry.length < PIN_LENGTH) return@LaunchedEffect
        delay(150) // Let the last dot fill before the keypad resets.
        when (first) {
            null -> first = entry
            entry -> { onDone(entry); return@LaunchedEffect }
            else -> { first = null; error = "PINs didn't match. Start again." }
        }
        entry = ""
    }
    val type: (Char) -> Unit = { if (entry.length < PIN_LENGTH) { entry += it; error = null } }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text(if (first == null) "Create your PIN" else "Confirm your PIN",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
                PinDots(entry.length)
                Text(error ?: if (first == null)
                    "Use 6 digits. Forgotten PINs can't be recovered. Clearing Notifly's app data resets your PIN and deletes local data."
                    else "Enter the same 6-digit PIN again.",
                    Modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                PinKeypad(entry, type, onDelete = { entry = entry.dropLast(1) }) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * Submits a complete PIN to [verify] and calls [onUnlock] for an Ok result or biometric success.
 * When [biometric] is enabled, prompts once on resume per composition and allows manual retries.
 * Wrong PIN and lockout results are displayed; exceptions from [verify] are not caught here.
 */
@Composable
fun LockScreen(
    verify: suspend (String) -> PinResult,
    lockoutSeconds: suspend () -> Long,
    onUnlock: () -> Unit,
    biometric: Boolean,
    authenticateBiometric: (onSuccess: () -> Unit) -> Unit,
) {
    // Keep the entered PIN in memory only, just like PIN setup.
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val biometricUnlock: () -> Unit = { scope.launch {
        val seconds = lockoutSeconds()
        if (seconds > 0) error = "Too many attempts. Try again in ${seconds}s." else onUnlock()
    } }
    // The lock is composed on ON_STOP, and a prompt raised while backgrounded is dropped; wait for resume, prompt once per lock.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value == Lifecycle.State.RESUMED
    var prompted by remember { mutableStateOf(false) }
    LaunchedEffect(biometric, resumed) { if (biometric && resumed && !prompted) { prompted = true; authenticateBiometric(biometricUnlock) } }
    LaunchedEffect(entry) {
        if (entry.length < PIN_LENGTH) return@LaunchedEffect
        delay(150) // Show the final dot before checking the PIN.
        error = when (val result = verify(entry)) {
            PinResult.Ok -> { onUnlock(); null }
            PinResult.Wrong -> "Incorrect PIN. Try again."
            is PinResult.LockedFor -> "Too many attempts. Try again in ${result.seconds}s."
        }
        entry = ""
    }
    val type: (Char) -> Unit = { if (entry.length < PIN_LENGTH) { entry += it; error = null } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text("Unlock Notifly", textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
            PinDots(entry.length)
            Text(error ?: "Enter your 6-digit PIN. Forgotten it? Clearing Notifly's app data resets your PIN and deletes local data.",
                Modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            PinKeypad(entry, type, onDelete = { entry = entry.dropLast(1) }) {
                if (biometric) TextButton(onClick = { authenticateBiometric(biometricUnlock) }) {
                    Text("Use\nbiometrics", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
