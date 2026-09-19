package ph.notifly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.NotiflyTheme

/**
 * Entry point for the shared UI. Screens get built out from the prototype:
 * onboarding → auth → home / transactions / insights / settings → CRUD → log.
 */
@Composable
fun NotiflyApp() {
    var palette by remember { mutableStateOf(NotiflyPalette.Evergreen) }

    NotiflyTheme(palette = palette) {
        Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
            Column(Modifier.padding(inner).padding(24.dp)) {
                Text("Notifly", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Scaffold is wired. Build screens here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
