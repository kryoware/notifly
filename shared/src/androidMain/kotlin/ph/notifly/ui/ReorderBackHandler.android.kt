package ph.notifly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun ReorderBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
