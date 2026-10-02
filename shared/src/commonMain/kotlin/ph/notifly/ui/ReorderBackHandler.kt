package ph.notifly.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun ReorderBackHandler(enabled: Boolean, onBack: () -> Unit)
