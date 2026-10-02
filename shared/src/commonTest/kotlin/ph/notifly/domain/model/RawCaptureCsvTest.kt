package ph.notifly.domain.model

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
    fun noRawExportParameter() {
        // Verify toCsv() has no raw parameter — the function signature must be
        // List<RawCapture>.toCsv(): String with no optional arguments.
        // This test simply ensures the metadata-only export works.
        val csv = listOf(RawCapture(
            id = 1,
            sourceApp = "A",
            capturedAt = Instant.fromEpochMilliseconds(0),
            body = "secret notification text",
            result = CaptureResult.PARSED,
            reason = "r",
            extras = mapOf("android.title" to "T"),
        )).toCsv()

        // The body and extras must NOT appear anywhere in the output.
        assertEquals(false, csv.contains("secret notification text"))
        assertEquals(false, csv.contains("android.title"))
        assertEquals(false, csv.contains("body"))
    }
}
