package ph.notifly.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val icons = LruCache<String, ImageBitmap>(200)

@Composable
actual fun AppIcon(packageName: String?, label: String, modifier: Modifier, size: Dp) {
    if (packageName == null) return LetterAvatar(label, modifier, size)
    val packageManager = LocalContext.current.packageManager
    val px = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState(icons.get(packageName), packageName) {
        value = icons.get(packageName)
            ?: withContext(Dispatchers.IO) { load(packageManager, packageName, px) }?.also { icons.put(packageName, it) }
    }
    val bitmap = icon
    if (bitmap == null) LetterAvatar(label, modifier, size)
    else Image(bitmap, contentDescription = null, modifier = modifier.size(size).clip(CircleShape))
}

/** Renders an app icon to a square of [px] pixels; a missing package returns null, other failures propagate. */
private fun load(packageManager: PackageManager, packageName: String, px: Int): ImageBitmap? = try {
    val drawable = packageManager.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
} catch (_: PackageManager.NameNotFoundException) { null }
