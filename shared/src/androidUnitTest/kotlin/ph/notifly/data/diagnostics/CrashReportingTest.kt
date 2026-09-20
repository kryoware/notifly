package ph.notifly.data.diagnostics

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import io.sentry.protocol.Message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrashReportingTest {
    private fun sentryExceptionFor(throwable: Throwable) = SentryException().apply {
        type = throwable::class.simpleName
        value = throwable.message
        stacktrace = io.sentry.protocol.SentryStackTrace().apply {
            frames = listOf(io.sentry.protocol.SentryStackFrame())
        }
    }

    private fun eventWith(throwable: Throwable): SentryEvent {
        val event = SentryEvent(throwable)
        event.exceptions = listOf(sentryExceptionFor(throwable))
        return event
    }

    @Test fun scrubDropsMessageKeepsTypeAndStack() {
        val notificationBody = "GCash: You received PHP 500.00 from JUAN D"
        val throwable = NumberFormatException("For input string: \"$notificationBody\"")
        val event = eventWith(throwable)

        val scrubbed = scrub(event)

        assertNull(scrubbed.exceptions!![0].value)
        assertEquals("NumberFormatException", scrubbed.exceptions!![0].type)
        assertEquals(1, scrubbed.exceptions!![0].stacktrace!!.frames!!.size)
    }

    @Test fun scrubDropsMessageOnEveryChainedException() {
        val notificationBody = "GCash: You received PHP 500.00 from JUAN D"
        val cause = NumberFormatException(notificationBody)
        val throwable = IllegalStateException("capture failed", cause)
        val event = SentryEvent(throwable)
        event.exceptions = listOf(sentryExceptionFor(cause), sentryExceptionFor(throwable))

        val scrubbed = scrub(event)

        assertNull(scrubbed.exceptions!![0].value)
        assertNull(scrubbed.exceptions!![1].value)
    }

    @Test fun scrubDropsBreadcrumbs() {
        val event = SentryEvent()
        event.breadcrumbs = mutableListOf(Breadcrumb().apply { message = "user tapped GCash — PHP 500.00" })

        val scrubbed = scrub(event)

        assertNull(scrubbed.breadcrumbs)
    }

    @Test fun scrubDropsTopLevelMessage() {
        val event = SentryEvent()
        event.message = Message().apply { formatted = "notification content should not appear here" }

        val scrubbed = scrub(event)

        assertNull(scrubbed.message)
    }

    @Test fun beforeSendGatedOnTransmitting() {
        val event = eventWith(RuntimeException("boom"))

        CrashReporting.transmitting = false
        assertNull(beforeSend(event))

        CrashReporting.transmitting = true
        val result = beforeSend(event)
        assertEquals("RuntimeException", result!!.exceptions!![0].type)
        assertNull(result.exceptions!![0].value)
    }
}
