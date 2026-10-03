package ph.notifly.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ph.notifly.ui.theme.tabular

/** Parse centavos without rounding or floating point; reject overflow and sub-cent values. */
fun parseAmountMinor(text: String): Long? {
    val value = text.trim()
    if (!Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(value)) return null
    val parts = value.split('.')
    val whole = parts[0].toLongOrNull() ?: return null
    val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
    if (whole > (Long.MAX_VALUE - cents) / 100) return null
    return (whole * 100 + cents).takeIf { it > 0 }
}

fun amountText(minor: Long): String {
    val digits = minor.toString().removePrefix("-").padStart(3, '0')
    return (if (minor < 0) "-" else "") + digits.dropLast(2) + "." + digits.takeLast(2)
}

/** A zero fee is valid only when entered explicitly. Blank, negative and sub-cent inputs fail. */
internal fun parseFeeMinor(text: String): Long? = parseAmountMinor(text)
    ?: 0L.takeIf { Regex("0+(\\.0{1,2})?").matches(text.trim()) }

/** Thousands-grouped display only; never fed back into [parseAmountMinor]. A true minus leads: −₱1,529.00. */
fun money(minor: Long): String {
    val text = amountText(minor)
    val negative = text.startsWith("-")
    val (whole, cents) = text.removePrefix("-").split(".")
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return "${if (negative) "−" else ""}₱$grouped.$cents"
}

/** Keeps digits, one point with at most two decimals, and a leading minus when [signed]; pasted commas drop. Null rejects the edit. */
internal fun amountInput(text: String, signed: Boolean): String? {
    val clean = text.replace(",", "")
    return clean.takeIf { Regex(if (signed) "-?[0-9]*(\\.[0-9]{0,2})?" else "[0-9]*(\\.[0-9]{0,2})?").matches(it) }
}

/** Settles typed input once focus leaves: `1234.5` becomes `1234.50`. Input without digits stays as typed. */
internal fun padCents(text: String): String {
    val match = Regex("(-?)([0-9]*)(?:\\.([0-9]{0,2}))?").matchEntire(text) ?: return text
    val (sign, whole, cents) = match.destructured
    if (whole.isEmpty() && cents.isEmpty()) return text
    return "$sign${whole.trimStart('0').ifEmpty { "0" }}.${cents.padEnd(2, '0')}"
}

/** Draws commas into the whole part on screen only, so field values stay plain for [parseAmountMinor]. */
internal object GroupedAmount : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val sign = if (raw.startsWith("-")) 1 else 0
        val wholeEnd = raw.indexOf('.').takeIf { it >= 0 } ?: raw.length
        val out = StringBuilder()
        val toOut = IntArray(raw.length + 1)
        raw.forEachIndexed { i, c ->
            if (i > sign && i < wholeEnd && (wholeEnd - i) % 3 == 0) out.append(',')
            toOut[i] = out.length
            out.append(c)
        }
        toOut[raw.length] = out.length
        val toRaw = IntArray(out.length + 1)
        var o = 0
        for (t in 0..out.length) {
            while (o < raw.length && toOut[o] < t) o++
            toRaw[t] = o
        }
        return TransformedText(AnnotatedString(out.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toOut[offset]
            override fun transformedToOriginal(offset: Int) = toRaw[offset]
        })
    }
}

/** The one money input: groups thousands as typed and pads cents on blur. [signed] admits a leading minus. */
@Composable
internal fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { amountInput(it, signed)?.let(onValueChange) },
        modifier = modifier.onFocusChanged { if (!it.isFocused) padCents(value).let { padded -> if (padded != value) onValueChange(padded) } },
        label = label, placeholder = placeholder, prefix = prefix, supportingText = supportingText, isError = isError,
        visualTransformation = GroupedAmount, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        textStyle = LocalTextStyle.current.tabular(), singleLine = true,
    )
}
