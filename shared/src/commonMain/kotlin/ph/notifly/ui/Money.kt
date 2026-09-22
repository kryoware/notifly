package ph.notifly.ui

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

/** Thousands-grouped display only; never fed back into [parseAmountMinor]. */
fun money(minor: Long): String {
    val text = amountText(minor)
    val negative = text.startsWith("-")
    val (whole, cents) = text.removePrefix("-").split(".")
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return "₱${if (negative) "-" else ""}$grouped.$cents"
}
