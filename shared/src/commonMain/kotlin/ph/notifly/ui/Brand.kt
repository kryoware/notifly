package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ph.notifly.ui.theme.accents
import ph.notifly.ui.theme.voice
import ph.notifly.ui.theme.Space

/** The n whose last stroke is a tick. */
@Composable
fun NotiflyMark(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(Res.drawable.notifly_mark), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
internal fun Wordmark(suffix: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        NotiflyMark(size = 26.dp)
        Text("notifly", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.045).em))
        suffix?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Pending is an open ring; confirming closes it into a filled tick. Decorative: rows announce status in words. */
@Composable
internal fun StatusMark(confirmed: Boolean, size: Dp = 22.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (confirmed) Box(Modifier.fillMaxSize().background(MaterialTheme.accents.confirmed, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.symbol_check), null, Modifier.size(size * 0.68f), tint = MaterialTheme.accents.onConfirmed)
        }
        else Box(Modifier.size(size * 0.64f).border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape))
    }
}

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, topPadding: Dp = 24.dp, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = topPadding).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
internal fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically)) {
        NotiflyMark(size = 40.dp, tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.voice, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Mono stamp under the mark, as on the brand sheet. */
@Composable
internal fun BrandFooter(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        NotiflyMark(size = 28.dp)
        Text("READ ON-DEVICE · COUNTED BY YOU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun brandCardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** Amount with the centavos dimmed, as the brand sets money. */
internal fun splitMoney(minor: Long, cents: Color) = buildAnnotatedString {
    val text = money(minor)
    val dot = text.lastIndexOf('.')
    append(text.substring(0, dot))
    withStyle(SpanStyle(color = cents)) { append(text.substring(dot)) }
}

/** Sign in front of the currency, with a true minus: −₱1,529.00. */
internal fun signedMoney(minor: Long) = (if (minor > 0) "+" else "") + money(minor)
