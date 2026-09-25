package ph.notifly.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture

/**
 * Tests for [FakeCaptureRepository], exercising the contract
 * that the real [CaptureRepositoryImpl] must also satisfy.
 */
class CaptureRepositoryTest {

    private fun capture(
        body: String? = "You received PHP 1,000.00 from TEST.",
        result: CaptureResult = CaptureResult.PARSED,
        capturedAt: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000),
    ) = RawCapture(
        sourceApp = "com.test.app",
        capturedAt = capturedAt,
        body = body,
        result = result,
        matchedAmount = "PHP 1,000.00",
        matchedDirection = "received",
        reason = "Test capture",
    )

    @Test
    fun `redactBodies blanks all bodies`() = runTest {
        val repo = FakeCaptureRepository()
        repo.record(capture(body = "sensitive text 1"))
        repo.record(capture(body = "sensitive text 2"))

        repo.redactBodies()

        val all = repo.observeLog().first()
        assertEquals(2, all.size)
        all.forEach { assertNull(it.body) }
    }

    @Test
    fun `purgeExpired drops captures older than RETENTION_HOURS`() = runTest {
        val now = Instant.fromEpochMilliseconds(1_700_100_000_000)
        val fixedClock = object : Clock {
            override fun now(): Instant = now
        }
        val repo = FakeCaptureRepository(clock = fixedClock)

        val fresh = capture(capturedAt = now.minus(1.hours))
        val stale = capture(capturedAt = now.minus((RawCapture.RETENTION_HOURS + 1).hours))

        repo.record(fresh)
        repo.record(stale)

        repo.purgeExpired()

        val remaining = repo.observeLog().first()
        assertEquals(1, remaining.size)
        assertEquals(fresh.copy(id = remaining.first().id), remaining.first())
    }

    @Test
    fun `observeLog filters by result`() = runTest {
        val repo = FakeCaptureRepository()
        repo.record(capture(result = CaptureResult.PARSED))
        repo.record(capture(result = CaptureResult.UNRECOGNIZED))
        repo.record(capture(result = CaptureResult.PARSED))
        repo.record(capture(result = CaptureResult.IGNORED))

        val parsed = repo.observeLog(CaptureResult.PARSED).first()
        assertEquals(2, parsed.size)
        assertTrue(parsed.all { it.result == CaptureResult.PARSED })
        assertTrue(repo.observeLog().first().none { it.result == CaptureResult.IGNORED })
    }

    @Test
    fun `clearLog removes everything`() = runTest {
        val repo = FakeCaptureRepository()
        repo.record(capture())
        repo.record(capture())

        repo.clearLog()

        assertEquals(0, repo.observeLog().first().size)
    }
}
