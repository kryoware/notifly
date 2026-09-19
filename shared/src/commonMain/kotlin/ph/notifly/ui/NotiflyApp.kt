package ph.notifly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val preferences = org.koin.compose.koinInject<ph.notifly.data.local.AppPreferences>()
    val palette by preferences.palette.collectAsState(NotiflyPalette.Evergreen)

    NotiflyTheme(palette = palette) {
        ph.notifly.ui.theme.ThemeGallery(preferences)
    }
}
