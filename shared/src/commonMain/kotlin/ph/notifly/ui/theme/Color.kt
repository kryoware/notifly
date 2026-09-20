package ph.notifly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Generated from the Figma file's `M3 Color` collection, which aliases `M3 Tones`.
 * File key: BTqrcTY3MPDY5WeDng5dzi
 *
 * Do NOT hand-edit hex values here. Change them in Figma and regenerate, so the
 * design file stays the single source of truth.
 *
 * Only light schemes exist today. Dark schemes need a second tonal ramp in Figma
 * (tones 20/30/80 for accents, 4/6/12/17/22/24 for neutrals).
 */

enum class NotiflyPalette { Evergreen, Indigo, Slate, Clay }

val NotiflyPalette.hint: String
    get() = when (this) {
        NotiflyPalette.Evergreen -> "Green"
        NotiflyPalette.Indigo -> "Purple"
        NotiflyPalette.Slate -> "Blue"
        NotiflyPalette.Clay -> "Terracotta"
    }

/** MD3 has no income/expense roles. These ride alongside the scheme. */
data class NotiflyAccents(
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
)

private val EvergreenScheme = lightColorScheme(
    primary = Color(0xFF22684B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2C8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D6),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFEAF8),
    onTertiaryContainer = Color(0xFF001F27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEBF0E9),
    surfaceContainerHigh = Color(0xFFE5EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DE),
    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC0C9C0),
    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF2EC),
    scrim = Color(0xFF000000),
)

private val IndigoScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    scrim = Color(0xFF000000),
)

private val SlateScheme = lightColorScheme(
    primary = Color(0xFF35566E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E4F6),
    onSecondaryContainer = Color(0xFF0D1D2A),
    tertiary = Color(0xFF67587A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF221533),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F8),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E9ED),
    surfaceContainerHighest = Color(0xFFE0E3E8),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFEFF1F4),
    scrim = Color(0xFF000000),
)

private val ClayScheme = lightColorScheme(
    primary = Color(0xFF8F4C38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3A0B01),
    secondary = Color(0xFF77574E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFF6C5D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1A7),
    onTertiaryContainer = Color(0xFF231B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFFE9E4),
    surfaceContainerHigh = Color(0xFFFFE2DB),
    surfaceContainerHighest = Color(0xFFF8D9D2),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    scrim = Color(0xFF000000),
)

internal fun schemeFor(palette: NotiflyPalette): ColorScheme = when (palette) {
    NotiflyPalette.Evergreen -> EvergreenScheme
    NotiflyPalette.Indigo -> IndigoScheme
    NotiflyPalette.Slate -> SlateScheme
    NotiflyPalette.Clay -> ClayScheme
}

internal fun accentsFor(palette: NotiflyPalette): NotiflyAccents = when (palette) {
    NotiflyPalette.Evergreen -> NotiflyAccents(
        Color(0xFF1E6B45), Color(0xFFA4F2C4), Color(0xFF002110),
        Color(0xFFBA1A1A), Color(0xFFFFDAD6), Color(0xFF410002),
    )
    NotiflyPalette.Indigo -> NotiflyAccents(
        Color(0xFF2E6B4F), Color(0xFFB7EFD0), Color(0xFF00210F),
        Color(0xFFB3261E), Color(0xFFF9DEDC), Color(0xFF410E0B),
    )
    NotiflyPalette.Slate -> NotiflyAccents(
        Color(0xFF2A6A47), Color(0xFFACF2C8), Color(0xFF002110),
        Color(0xFFBA1A1A), Color(0xFFFFDAD6), Color(0xFF410002),
    )
    NotiflyPalette.Clay -> NotiflyAccents(
        Color(0xFF3C6B45), Color(0xFFBEF0C4), Color(0xFF00210B),
        Color(0xFFBA1A1A), Color(0xFFFFDAD6), Color(0xFF410002),
    )
}
