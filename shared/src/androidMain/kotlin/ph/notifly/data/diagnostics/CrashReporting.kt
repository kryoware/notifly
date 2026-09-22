package ph.notifly.data.diagnostics

import android.app.Application
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite

internal fun scrub(event: SentryEvent): SentryEvent {
    event.exceptions?.forEach { it.value = null }
    event.message = null
    event.breadcrumbs = null
    return event
}

internal fun beforeSend(event: SentryEvent): SentryEvent? = if (CrashReporting.transmitting) scrub(event) else null

object CrashReporting {
    @Volatile
    var transmitting = false

    @Volatile
    private var initialized = false

    @Synchronized
    fun setEnabled(enabled: Boolean, app: Application, dsn: String) {
        if (!enabled || dsn.isBlank()) {
            transmitting = false
            if (initialized) {
                Sentry.close()
                initialized = false
            }
            return
        }
        transmitting = true
        if (!initialized) {
            SentryAndroid.init(app) { o ->
                o.dsn = dsn
                o.isSendDefaultPii = false
                o.isAttachScreenshot = false
                o.isAttachViewHierarchy = false
                o.sessionReplay.sessionSampleRate = 0.0
                o.sessionReplay.onErrorSampleRate = 0.0
                o.logs.isEnabled = true
                o.tracesSampleRate = 1.0
                o.metrics.isEnabled = true
                o.setBeforeSend { event, _ -> beforeSend(event) }
                o.setBeforeSendTransaction { transaction, _ ->
                    if (transmitting) transaction else null
                }
                o.logs.beforeSend = { log -> if (transmitting) log else null }
                o.metrics.beforeSend = { metric, _ -> if (transmitting) metric else null }
            }
            initialized = true
        }
    }

    fun <T> trace(name: String, block: () -> T): T {
        if (!transmitting) return block()
        val transaction: ITransaction = Sentry.startTransaction(name, "app.operation")
        return try {
            block()
        } finally {
            transaction.finish()
        }
    }

    fun log(message: String) {
        if (transmitting) Sentry.logger().info(message)
    }

    fun count(metric: String) {
        if (transmitting) Sentry.metrics().count(metric, 1.0)
    }
}

class SentryErrorReporter : ErrorReporter {
    override fun report(throwable: Throwable, site: ErrorSite) {
        CrashReporting.trace("error.report") {
            CrashReporting.log("Error reported at ${site.name}")
            CrashReporting.count("errors.reported")
            Sentry.captureException(throwable) { it.setTag("site", site.name) }
        }
    }
}
