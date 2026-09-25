package ph.notifly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNotiflyAccents = staticCompositionLocalOf {
    accentsFor(NotiflyPalette.Evergreen)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Reach for income/expense colours via `MaterialTheme.accents`. */
val MaterialTheme.accents: NotiflyAccents
    @Composable get() = LocalNotiflyAccents.current

@Composable
fun NotiflyTheme(
    palette: NotiflyPalette = NotiflyPalette.Evergreen,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalNotiflyAccents provides accentsFor(palette, dark)) {
        MaterialExpressiveTheme(
            colorScheme = schemeFor(palette, dark),
            content = content,
        )
    }
}
