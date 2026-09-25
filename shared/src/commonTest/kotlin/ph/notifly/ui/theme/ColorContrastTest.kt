package ph.notifly.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorContrastTest {
    @Test
    fun semanticTextAccentsMeetMinimumContrastInEveryTheme() {
        NotiflyPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val accents = accentsFor(palette, dark)
                val scheme = schemeFor(palette, dark)

                listOf(scheme.surface, scheme.surfaceContainerLow).forEach { surface ->
                    assertContrast(palette, dark, "income", accents.income, surface)
                    assertContrast(palette, dark, "expense", accents.expense, surface)
                    assertContrast(palette, dark, "onSurface", scheme.onSurface, surface)
                    assertContrast(palette, dark, "onSurfaceVariant", scheme.onSurfaceVariant, surface)
                }
                listOf(
                    "primary" to (scheme.onPrimary to scheme.primary),
                    "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "secondary" to (scheme.onSecondary to scheme.secondary),
                    "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                    "tertiary" to (scheme.onTertiary to scheme.tertiary),
                    "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                    "error" to (scheme.onError to scheme.error),
                    "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                    "inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                    "incomeContainer" to (accents.onIncomeContainer to accents.incomeContainer),
                    "expenseContainer" to (accents.onExpenseContainer to accents.expenseContainer),
                ).forEach { (role, colors) -> assertContrast(palette, dark, role, colors.first, colors.second) }
                assertContrast(palette, dark, "outline", scheme.outline, scheme.surface, 3.0)
                assertContrast(palette, dark, "selected control", scheme.primary, scheme.surface, 3.0)
            }
        }
    }

    private fun assertContrast(
        palette: NotiflyPalette,
        dark: Boolean,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Double = 4.5,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$palette ${if (dark) "dark" else "light"} $role contrast was $ratio")
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = first.relativeLuminance()
        val secondLuminance = second.relativeLuminance()
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        0.2126 * red.linearized() + 0.7152 * green.linearized() + 0.0722 * blue.linearized()

    private fun Float.linearized(): Double =
        if (this <= 0.04045f) this / 12.92 else ((this + 0.055) / 1.055).pow(2.4)
}
