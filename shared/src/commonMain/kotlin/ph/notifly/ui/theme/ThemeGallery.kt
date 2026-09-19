package ph.notifly.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ph.notifly.ui.SettingsModel

/** Side-by-side inspection of generated Figma roles; horizontally scroll on phones. */
@Composable
fun ThemeGallery(model: SettingsModel) {
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NotiflyPalette.entries.forEach { palette ->
            NotiflyTheme(palette) {
                val c = MaterialTheme.colorScheme
                val a = MaterialTheme.accents
                val roles = listOf(
                    "primary" to c.primary, "onPrimary" to c.onPrimary,
                    "primaryContainer" to c.primaryContainer, "onPrimaryContainer" to c.onPrimaryContainer,
                    "inversePrimary" to c.inversePrimary,
                    "secondary" to c.secondary, "onSecondary" to c.onSecondary,
                    "secondaryContainer" to c.secondaryContainer, "onSecondaryContainer" to c.onSecondaryContainer,
                    "tertiary" to c.tertiary, "onTertiary" to c.onTertiary,
                    "tertiaryContainer" to c.tertiaryContainer, "onTertiaryContainer" to c.onTertiaryContainer,
                    "background" to c.background, "onBackground" to c.onBackground,
                    "surface" to c.surface, "onSurface" to c.onSurface,
                    "surfaceVariant" to c.surfaceVariant, "onSurfaceVariant" to c.onSurfaceVariant,
                    "surfaceTint" to c.surfaceTint, "inverseSurface" to c.inverseSurface,
                    "inverseOnSurface" to c.inverseOnSurface,
                    "surfaceBright" to c.surfaceBright, "surfaceDim" to c.surfaceDim,
                    "surfaceContainerLowest" to c.surfaceContainerLowest,
                    "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainer" to c.surfaceContainer,
                    "surfaceContainerHigh" to c.surfaceContainerHigh,
                    "surfaceContainerHighest" to c.surfaceContainerHighest,
                    "error" to c.error, "onError" to c.onError,
                    "errorContainer" to c.errorContainer, "onErrorContainer" to c.onErrorContainer,
                    "outline" to c.outline, "outlineVariant" to c.outlineVariant, "scrim" to c.scrim,
                    "income" to a.income, "incomeContainer" to a.incomeContainer,
                    "onIncomeContainer" to a.onIncomeContainer, "expense" to a.expense,
                    "expenseContainer" to a.expenseContainer, "onExpenseContainer" to a.onExpenseContainer,
                )
                Surface(Modifier.width(280.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(palette.name, style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { model.palette(palette) }) { Text("Use ${palette.name}") }
                        roles.forEach { (name, color) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(Modifier.size(32.dp), color = color, border = BorderStroke(1.dp, c.outline)) {}
                                Text(name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
