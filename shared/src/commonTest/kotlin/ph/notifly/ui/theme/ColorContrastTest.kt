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
                }
            }
        }
    }

    private fun assertContrast(
        palette: NotiflyPalette,
        dark: Boolean,
        role: String,
        foreground: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5, "$palette ${if (dark) "dark" else "light"} $role contrast was $ratio")
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
