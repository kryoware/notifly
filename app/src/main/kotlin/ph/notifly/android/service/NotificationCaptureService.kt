package ph.notifly.android.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite
import ph.notifly.domain.source.NotificationContent
import ph.notifly.domain.source.NotificationEvent
import ph.notifly.domain.source.NotificationTransactionSource
import ph.notifly.domain.source.TransactionSource

/** Notification content must never appear in diagnostics or network requests. */
class NotificationCaptureService : NotificationListenerService() {
    private val source: TransactionSource by inject()
    private val reporter: ErrorReporter by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            try { processing.withLock { source.capture(sbn.toEvent()) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                reporter.report(e, ErrorSite.NOTIFICATION_CAPTURE)
                (source as NotificationTransactionSource).storageError()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        (source as NotificationTransactionSource).connected(true)
        activeNotifications?.forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        (source as NotificationTransactionSource).connected(false)
        requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        (source as NotificationTransactionSource).connected(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun StatusBarNotification.toEvent(): NotificationEvent {
        val extras = notification?.extras
        return NotificationEvent(
            key = key,
            sourceApp = packageName,
            postedAtMillis = postTime,
            readContent = {
                NotificationContent(
                    title = extras?.getCharSequence("android.title")?.toString().orEmpty(),
                    text = (extras?.getCharSequence("android.bigText")
                        ?: extras?.getCharSequence("android.text"))?.toString().orEmpty(),
                )
            },
        )
    }
}
