package ph.notifly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Owned by Android's Activity ViewModel, so rotation resumes the stroke instead of replaying it. */
class LaunchAnimationState {
    internal val trace = Animatable(0f)
    internal val opacity = Animatable(1f)
    var complete by mutableStateOf(false)
        private set

    fun skip() { complete = true }
}

@Composable
internal fun LaunchSplash(state: LaunchAnimationState, start: Boolean, animationsEnabled: Boolean, onComplete: () -> Unit) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    LaunchedEffect(state, start, animationsEnabled) {
        if (!animationsEnabled) {
            state.skip()
            currentOnComplete()
        } else if (start) {
            // Let the first themed frame replace the neutral starting window before drawing the mark.
            withFrameNanos { }
            state.trace.animateTo(1f, tween((450 * (1f - state.trace.value)).toInt(), easing = LinearEasing))
            state.opacity.animateTo(0f, tween((150 * state.opacity.value).toInt(), easing = LinearEasing))
            state.skip()
            currentOnComplete()
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    // Exact path and stroke from composeResources/drawable/notifly_mark.xml.
    val measure = remember { PathMeasure().apply {
        setPath(PathParser().parsePathString("M16,78.5 V44.5 A23,23 0 0 1 62,44.5 V74.5 L84,48.5").toPath(), false)
    } }
    Surface(Modifier.fillMaxSize().graphicsLayer { alpha = state.opacity.value }.pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(96.dp)) {
                val segment = Path()
                measure.getSegment(0f, measure.length * state.trace.value, segment)
                scale(size.width / 100f, size.height / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(segment, primary, style = Stroke(16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
