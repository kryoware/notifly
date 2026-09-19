package ph.notifly.domain.model

fun List<RawCapture>.toCsv(): String = buildString {
    appendLine("id,source_app,captured_at,result,matched_amount,matched_direction,reason,raw_body")
    this@toCsv.forEach { capture ->
        appendLine(listOf(
            capture.id,
            capture.sourceApp,
            capture.capturedAt,
            capture.result,
            capture.matchedAmount,
            capture.matchedDirection,
            capture.reason,
            capture.body,
        ).joinToString(",") { it.csvField() })
    }
}

private fun Any?.csvField(): String = (this?.toString() ?: "").replace("\"", "\"\"").let { "\"$it\"" }
