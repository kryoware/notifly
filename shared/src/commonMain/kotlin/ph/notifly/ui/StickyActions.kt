package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val LocalAppSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
internal fun StickyActionSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** The owning destination already consumes system bars; this scaffold consumes the remaining IME inset. */
@Composable
internal fun StickyActionScaffold(
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val snackbar = LocalAppSnackbar.current ?: remember { SnackbarHostState() }
    Scaffold(Modifier.fillMaxSize().imePadding(), contentWindowInsets = WindowInsets(0),
        bottomBar = { StickyActionSurface(actions) },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 8.dp)) }) { padding ->
        content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
    }
}
