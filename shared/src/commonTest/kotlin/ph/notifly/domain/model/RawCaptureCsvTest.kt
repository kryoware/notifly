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
            "id,source_app,captured_at,result,matched_amount,matched_direction,reason,raw_body\n" +
                "\"7\",\"Wallet, Inc.\",\"1970-01-01T00:00:00Z\",\"UNRECOGNIZED\",\"\",\"\",\"needs \"\"review\"\"\",\"\"\n",
            csv,
        )
    }
}
