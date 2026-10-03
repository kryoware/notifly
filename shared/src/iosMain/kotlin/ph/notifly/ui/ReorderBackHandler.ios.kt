package ph.notifly.ui

import androidx.compose.runtime.Composable

// iOS has no system Back button; there is no iOS app host yet.
@Composable
internal actual fun ReorderBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
