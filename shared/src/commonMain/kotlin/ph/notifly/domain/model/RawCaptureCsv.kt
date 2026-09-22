package ph.notifly.domain.model

/** Returns metadata-only CSV; raw notification bodies never leave the device. */
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

private fun Any?.csvField(): String = (this?.toString() ?: "").replace("\"", "\"\"").let { "\"$it\"" }
