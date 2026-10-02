package ph.notifly.domain.model

/** Returns metadata-only CSV; notification content never leaves the device. */
fun List<RawCapture>.toCsv(): String = buildString {
    appendLine("id,source_app,captured_at,result,matched_amount,matched_direction,reason")
    this@toCsv.forEach { capture ->
        appendLine(listOf(
            capture.id,
            capture.sourceApp,
            capture.capturedAt,
            capture.result,
            capture.matchedAmount,
            capture.matchedDirection,
            capture.reason,
        ).joinToString(",") { it.csvField() })
    }
}

/**
 * Quotes a value for CSV. Doubles internal quotes per RFC 4180 and prefixes
 * formula-starting characters with a single-quote so spreadsheets treat the
 * cell as plain text rather than evaluating it.
 */
private fun Any?.csvField(): String {
    val s = this?.toString() ?: ""
    val escaped = s.replace("\"", "\"\"")
    val safe = if (escaped.isNotEmpty() && escaped[0] in FORMULA_CHARS) "'$escaped" else escaped
    return "\"$safe\""
}

private val FORMULA_CHARS = setOf('=', '+', '-', '@', '\t', '\r')
