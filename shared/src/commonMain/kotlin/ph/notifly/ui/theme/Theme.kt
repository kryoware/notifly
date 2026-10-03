package ph.notifly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import notifly.shared.generated.resources.*
import org.jetbrains.compose.resources.Font

val LocalNotiflyAccents = staticCompositionLocalOf {
    accentsFor(NotiflyPalette.Ube)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Reach for income/expense colours via `MaterialTheme.accents`. */
val MaterialTheme.accents: NotiflyAccents
    @Composable get() = LocalNotiflyAccents.current

/** Money columns line up only with tabular figures. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

// Chips, cards and the FAB from ube.css; dialogs and fields keep Material defaults.
// Not monotonic on purpose: Card reads medium (24) and the extended FAB reads large (20).
private val NotiflyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(20.dp),
)

private val TrayShape = RoundedCornerShape(18.dp)

/** The inset review tray inside the balance hero. */
val Shapes.tray: Shape get() = TrayShape

/** 4dp grid for padding and gaps. Component sizes (avatars, touch targets, icons) stay literal. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/** The confirmed balance: Geist SemiBold, tight, tabular. */
val Typography.balance: TextStyle
    get() = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em).tabular()

/** Fraunces italic, the brand's spoken line. Taglines and empty states only; never data, labels, or headings. */
val Typography.voice: TextStyle
    get() = displaySmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontStyle = FontStyle.Italic)

/** Geist for everything, Fraunces as the brand's voice on display lines, Geist Mono for stamps. */
@Composable
private fun notiflyTypography(): Typography {
    val geist = FontFamily(
        Font(Res.font.geist_regular),
        Font(Res.font.geist_medium, FontWeight.Medium),
        Font(Res.font.geist_semibold, FontWeight.SemiBold),
    )
    val voice = FontFamily(Font(Res.font.fraunces_regular), Font(Res.font.fraunces_italic, style = FontStyle.Italic))
    val mono = FontFamily(Font(Res.font.geist_mono_medium, FontWeight.Medium))
    fun TextStyle.geist() = copy(fontFamily = geist)
    return with(Typography()) {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = voice, letterSpacing = (-0.025).em),
            displayMedium = displayMedium.copy(fontFamily = voice, letterSpacing = (-0.025).em),
            displaySmall = displaySmall.copy(fontFamily = voice, letterSpacing = (-0.02).em),
            headlineLarge = headlineLarge.geist(), headlineMedium = headlineMedium.geist(), headlineSmall = headlineSmall.geist(),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.015).em).geist(), titleMedium = titleMedium.geist(), titleSmall = titleSmall.geist(),
            bodyLarge = bodyLarge.geist(), bodyMedium = bodyMedium.geist(), bodySmall = bodySmall.geist(),
            labelLarge = labelLarge.geist(), labelMedium = labelMedium.geist(),
            labelSmall = labelSmall.copy(fontFamily = mono, fontWeight = FontWeight.Medium, letterSpacing = 0.12.em),
            displayLargeEmphasized = displayLargeEmphasized.geist(), displayMediumEmphasized = displayMediumEmphasized.geist(),
            displaySmallEmphasized = displaySmallEmphasized.geist(), headlineLargeEmphasized = headlineLargeEmphasized.geist(),
            headlineMediumEmphasized = headlineMediumEmphasized.geist(), headlineSmallEmphasized = headlineSmallEmphasized.geist(),
            titleLargeEmphasized = titleLargeEmphasized.geist(), titleMediumEmphasized = titleMediumEmphasized.geist(),
            titleSmallEmphasized = titleSmallEmphasized.geist(), bodyLargeEmphasized = bodyLargeEmphasized.geist(),
            bodyMediumEmphasized = bodyMediumEmphasized.geist(), bodySmallEmphasized = bodySmallEmphasized.geist(),
            labelLargeEmphasized = labelLargeEmphasized.geist(), labelMediumEmphasized = labelMediumEmphasized.geist(),
            labelSmallEmphasized = labelSmallEmphasized.geist(),
        )
    }
}

@Composable
fun NotiflyTheme(
    palette: NotiflyPalette = NotiflyPalette.Ube,
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
            typography = notiflyTypography(),
            shapes = NotiflyShapes,
            content = content,
        )
    }
}
