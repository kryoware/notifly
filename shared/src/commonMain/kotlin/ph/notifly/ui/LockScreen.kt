package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.launch
import ph.notifly.data.local.PIN_LENGTH
import ph.notifly.data.local.PinResult

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

/** [setup] asks for a new PIN twice; otherwise asks for the current PIN before removing it. */
@Composable
internal fun PinDialog(setup: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val pin = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    val matches = confirm.text.toString() == pin.text.toString()
    val ready = pin.text.length == PIN_LENGTH && (!setup || matches)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (setup) "Set app PIN" else "Remove app PIN") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (setup) Text("A forgotten PIN can't be recovered. Clearing Notifly's app data resets it and deletes your local data.")
            PinField(pin, if (setup) "New PIN" else "Current PIN")
            if (setup) PinField(confirm, "Confirm PIN",
                error = "PINs don't match".takeIf { confirm.text.length == PIN_LENGTH && !matches },
                onDone = { if (ready) onDone(pin.text.toString()) })
        } },
        confirmButton = { TextButton(onClick = { onDone(pin.text.toString()) }, enabled = ready) { Text(if (setup) "Set PIN" else "Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    val pin = rememberTextFieldState()
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
    val submit: () -> Unit = { if (pin.text.length == PIN_LENGTH) scope.launch {
        error = when (val result = verify(pin.text.toString())) {
            PinResult.Ok -> { onUnlock(); null }
            PinResult.Wrong -> "Wrong PIN"
            is PinResult.LockedFor -> "Too many attempts. Try again in ${result.seconds}s."
        }
        pin.clearText()
    } }
    LaunchedEffect(pin.text) { submit() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("Notifly is locked", style = MaterialTheme.typography.headlineSmall)
            PinField(pin, "PIN", error = error, onDone = submit)
            Button(onClick = submit, enabled = pin.text.length == PIN_LENGTH) { Text("Unlock") }
            if (biometric) TextButton(onClick = { authenticateBiometric(biometricUnlock) }) { Text("Use biometrics") }
            Text("Forgot your PIN? Clearing Notifly's app data resets it and deletes your local data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
