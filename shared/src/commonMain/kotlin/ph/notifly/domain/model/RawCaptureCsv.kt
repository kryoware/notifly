package ph.notifly.domain.model

/**
 * Returns metadata-only CSV by default. Debug builds may include the stored body and extras.
 */
fun List<RawCapture>.toCsv(raw: Boolean = false): String = buildString {
    val keys = if (raw) this@toCsv.flatMap { it.extras.keys }.distinct().sorted() else emptyList()
    val rawHeader = if (raw) (listOf("body") + keys).joinToString("") { ",${it.csvField()}" } else ""
    appendLine("id,source_app,captured_at,result,matched_amount,matched_direction,reason$rawHeader")
    this@toCsv.forEach { capture ->
        val fields = listOf(
            capture.id,
            capture.sourceApp,
            capture.capturedAt,
            capture.result,
            capture.matchedAmount,
            capture.matchedDirection,
            capture.reason,
        ) + if (raw) listOf(capture.body) + keys.map { capture.extras[it] } else emptyList()
        appendLine(fields.joinToString(",") { it.csvField() })
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
