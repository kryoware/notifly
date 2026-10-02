package ph.notifly.domain.model

/**
 * Returns metadata-only CSV; raw notification bodies never leave the device.
 * [raw] (debug builds only) appends the stored body and one column per notification extra.
 */
fun List<RawCapture>.toCsv(raw: Boolean = false): String = buildString {
    val keys = if (raw) this@toCsv.flatMap { it.extras.keys }.distinct().sorted() else emptyList()
    val rawHeader = if (raw) (listOf("body") + keys).joinToString("") { ",${it.csvField()}" } else ""
    appendLine("id,source_app,captured_at,result,matched_amount,matched_direction,reason$rawHeader")
    this@toCsv.forEach { capture ->
        val rawFields = if (raw) listOf(capture.body) + keys.map { capture.extras[it] } else emptyList()
        appendLine((listOf(
            capture.id,
            capture.sourceApp,
            capture.capturedAt,
            capture.result,
            capture.matchedAmount,
            capture.matchedDirection,
            capture.reason,
        ) + rawFields).joinToString(",") { it.csvField() })
    }
}

private fun Any?.csvField(): String = (this?.toString() ?: "").replace("\"", "\"\"").let { "\"$it\"" }
