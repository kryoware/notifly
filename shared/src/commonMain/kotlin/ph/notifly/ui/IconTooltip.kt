package ph.notifly.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun mirroredIconModifier(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) Modifier.graphicsLayer(scaleX = -1f) else Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}
