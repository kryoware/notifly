package ph.notifly.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

/** Whether the platform can blur content; Android 8-11 can't, so [hiddenMoneyModifier] paints a pill instead. */
internal expect val canBlur: Boolean

/** Blur radius as a fraction of the text height, so headline and row amounts are equally unreadable. */
private const val BLUR_PER_HEIGHT = 0.4f
private const val PILL_ALPHA = 0.2f

/** Obscures a money figure and hides it from screen readers while [hidden]. */
@Composable
internal fun hiddenMoneyModifier(hidden: Boolean): Modifier {
    val color = LocalContentColor.current
    val obscured = if (canBlur) Modifier.graphicsLayer {
        val radius = if (hidden) size.height * BLUR_PER_HEIGHT else 0f
        renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
        clip = false
    } else Modifier.drawWithContent {
        if (hidden) drawRoundRect(color.copy(alpha = PILL_ALPHA), cornerRadius = CornerRadius(size.height / 2))
        else drawContent()
    }
    return if (hidden) obscured.clearAndSetSemantics { contentDescription = "Amount hidden" } else obscured
}
