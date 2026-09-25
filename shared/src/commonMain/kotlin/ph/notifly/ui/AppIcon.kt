package ph.notifly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Launcher icon for [packageName], or a letter avatar when there is no installed app to show. */
@Composable
expect fun AppIcon(packageName: String?, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp)

@Composable
internal fun LetterAvatar(label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label.take(1), style = if (size < 32.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium)
        }
    }
}

/** Captures store the app label, transactions the package; this resolves either to a package. */
internal fun Map<String, String>.packageFor(labelOrPackage: String?): String? =
    labelOrPackage?.takeIf { it in this } ?: entries.firstOrNull { it.value == labelOrPackage }?.key
