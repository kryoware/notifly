package ph.notifly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
actual fun AppIcon(packageName: String?, label: String, modifier: Modifier, size: Dp) = LetterAvatar(label, modifier, size)
