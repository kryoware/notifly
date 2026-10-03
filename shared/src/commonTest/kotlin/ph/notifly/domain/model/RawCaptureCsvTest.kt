package ph.notifly.domain.model

import ph.notifly.data.local.toDomain
import ph.notifly.data.local.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RawCaptureCsvTest {
    @Test
    fun escapesFieldsAndLeavesNullFieldsEmpty() {
        val csv = listOf(RawCapture(
            id = 7,
            sourceApp = "Wallet, Inc.",
            capturedAt = Instant.fromEpochMilliseconds(0),
            body = null,
            result = CaptureResult.UNRECOGNIZED,
            reason = "needs \"review\"",
        )).toCsv()

        assertEquals(
            "id,source_app,captured_at,result,matched_amount,matched_direction,reason\n" +
                "\"7\",\"Wallet, Inc.\",\"1970-01-01T00:00:00Z\",\"UNRECOGNIZED\",\"\",\"\",\"needs \"\"review\"\"\"\n",
            csv,
        )
    }

    @Test
    fun formulaStartingCharsAreNeutralised() {
        val csv = listOf(RawCapture(
            id = 1,
            sourceApp = "=cmd|calc|",
            capturedAt = Instant.fromEpochMilliseconds(0),
            body = null,
            result = CaptureResult.UNRECOGNIZED,
            reason = "+alert(1)",
        )).toCsv()

        // Source app and reason should have formula-starting chars neutralised with a leading '
        assertEquals(
            "id,source_app,captured_at,result,matched_amount,matched_direction,reason\n" +
                "\"1\",\"'=cmd|calc|\",\"1970-01-01T00:00:00Z\",\"UNRECOGNIZED\",\"\",\"\",\"'+alert(1)\"\n",
            csv,
        )
    }

    @Test
    fun exportExcludesBodyAndExtrasSurvivingStorage() {
        val tricky = "line1\nline\t2 \\n literal"
        val captures = listOf(
            RawCapture(id = 1, sourceApp = "A", capturedAt = Instant.fromEpochMilliseconds(0), body = "b1",
                result = CaptureResult.PARSED, reason = "r", extras = mapOf("android.title" to "T", "android.text" to tricky)),
            RawCapture(id = 2, sourceApp = "B", capturedAt = Instant.fromEpochMilliseconds(0), body = null,
                result = CaptureResult.UNRECOGNIZED, reason = "r", extras = mapOf("android.subText" to "S")),
        ).map { it.toEntity().toDomain() }

        assertEquals(tricky, captures[0].extras["android.text"])
        val metadataCsv = captures.toCsv()
        assertEquals(false, metadataCsv.contains("b1"))
        assertEquals(false, metadataCsv.contains("android.text"))
        assertEquals(false, metadataCsv.contains(tricky))
        assertEquals(false, metadataCsv.contains("android.title"))
    }
}
