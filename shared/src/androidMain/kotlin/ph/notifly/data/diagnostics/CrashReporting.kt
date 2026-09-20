package ph.notifly.data.diagnostics

import android.app.Application
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite

/** Removes message and breadcrumb text in place while retaining exception types and stack traces. */
internal fun scrub(event: SentryEvent): SentryEvent {
    event.exceptions?.forEach { it.value = null }
    event.message = null
    event.breadcrumbs = null
    return event
}

/** Drops events while consent is off; otherwise returns the privacy-scrubbed event. */
internal fun beforeSend(event: SentryEvent): SentryEvent? = if (CrashReporting.transmitting) scrub(event) else null

object CrashReporting {
    /** Flipped by the preference collector in NotiflyApplication; makes consent revocation immediate. */
    @Volatile
    var transmitting = false

    /**
     * Initializes Sentry with default PII, screenshots, view hierarchies, replay, logs, and tracing disabled.
     * A blank [dsn] is a no-op.
     */
    fun start(app: Application, dsn: String) {
        if (dsn.isBlank()) return
        SentryAndroid.init(app) { o ->
            o.dsn = dsn
            o.isSendDefaultPii = false
            o.isAttachScreenshot = false
            o.isAttachViewHierarchy = false
            o.sessionReplay.sessionSampleRate = 0.0
            o.sessionReplay.onErrorSampleRate = 0.0
            o.logs.isEnabled = false
            o.tracesSampleRate = 0.0
            o.setBeforeSend { event, _ -> beforeSend(event) }
        }
    }
}

class SentryErrorReporter : ErrorReporter {
    override fun report(throwable: Throwable, site: ErrorSite) {
        Sentry.captureException(throwable) { it.setTag("site", site.name) }
    }
}
