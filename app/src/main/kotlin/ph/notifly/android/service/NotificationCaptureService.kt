package ph.notifly.android.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import ph.notifly.data.parser.NotificationParser
import ph.notifly.data.parser.ParseOutcome
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import ph.notifly.domain.source.NotificationTransactionSource
import ph.notifly.domain.source.TransactionSource
import kotlin.time.Clock

/** Notification content must never appear in diagnostics or network requests. */
class NotificationCaptureService : NotificationListenerService() {
    private val allowList: AllowListRepository by inject()
    private val captures: CaptureRepository by inject()
    private val parser: NotificationParser by inject()
    private val source: TransactionSource by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            try { processing.withLock { capture(sbn) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { (source as NotificationTransactionSource).storageError() }
        }
    }

    private suspend fun capture(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val now = Clock.System.now()
        captures.purgeExpired()
        if (!allowList.isAllowed(pkg)) {
            captures.record(RawCapture(sourceApp = pkg, capturedAt = now, body = null,
                result = CaptureResult.IGNORED, reason = "App is not on your allow-list; its notification text was not read.",
                fingerprint = digest("ignored:${sbn.key}:${sbn.postTime}")))
            return
        }
        // Access text only after the explicit allow-list check.
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))?.toString().orEmpty()
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        if (body.isBlank()) return
        val fingerprint = digest("${sbn.key}\u0000$body")
        val id = when (val result = parser.parse(body)) {
            is ParseOutcome.Unrecognized -> captures.record(RawCapture(sourceApp = pkg, capturedAt = now, body = body,
                result = CaptureResult.UNRECOGNIZED, reason = result.reason, fingerprint = fingerprint))
            is ParseOutcome.Parsed -> {
                val draft = result.draft
                captures.recordParsed(RawCapture(sourceApp = pkg, capturedAt = now, body = body,
                    result = if (draft.needsReview) CaptureResult.NEEDS_REVIEW else CaptureResult.PARSED,
                    matchedAmount = draft.matchedAmount, matchedDirection = draft.matchedDirection,
                    reason = result.reason, fingerprint = fingerprint),
                    Transaction(title = draft.merchant ?: "Payment from $pkg", amountMinor = draft.amountMinor,
                        currency = draft.currency, type = draft.type, status = TransactionStatus.NEEDS_REVIEW,
                        category = "Other", occurredAt = now, sourceApp = pkg, captureId = null))
            }
        }
        if (id != -1L) allowList.incrementCapturedCount(pkg)
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
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
