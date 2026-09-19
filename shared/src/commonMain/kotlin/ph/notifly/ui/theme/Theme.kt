package ph.notifly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNotiflyAccents = staticCompositionLocalOf {
    accentsFor(NotiflyPalette.Evergreen)
}

/** Reach for income/expense colours via `MaterialTheme.accents`. */
val MaterialTheme.accents: NotiflyAccents
    @Composable get() = LocalNotiflyAccents.current

@Composable
fun NotiflyTheme(
    palette: NotiflyPalette = NotiflyPalette.Evergreen,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNotiflyAccents provides accentsFor(palette)) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = NotiflyTypography,
            content = content,
        )
    }
}
