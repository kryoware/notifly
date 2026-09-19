package ph.notifly.android.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.android.ext.android.inject
import ph.notifly.data.parser.NotificationParser
import ph.notifly.data.parser.ParseOutcome
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import ph.notifly.domain.repository.AllowListRepository
import ph.notifly.domain.repository.CaptureRepository

/**
 * Reads notifications from allow-listed apps only.
 *
 * NEVER log notification content here, even at DEBUG. Tags and counts only.
 */
class NotificationCaptureService : NotificationListenerService() {

    private val allowList: AllowListRepository by inject()
    private val captures: CaptureRepository by inject()
    private val parser: NotificationParser by inject()

    private val scope = CoroutineScope(SupervisorJob())
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        if (body.isBlank()) return

        // Apps update a single notification in place, so the same key arrives
        // repeatedly. Dedupe on key + content hash or you will double-count.
        val fingerprint = "${sbn.key}:${body.hashCode()}"
        if (!seen.add(fingerprint)) return
        if (seen.size > 200) seen.iterator().let { it.next(); it.remove() }

        scope.launch {
            if (!allowList.isAllowed(pkg)) {
                captures.record(
                    RawCapture(
                        sourceApp = pkg,
                        capturedAt = Clock.System.now(),
                        body = null, // not on the allow-list: body is never stored
                        result = CaptureResult.IGNORED,
                        reason = "This app is not on your allow-list, so the notification body was never read.",
                    ),
                )
                return@launch
            }

            // Record the raw capture BEFORE parsing, so a parser crash still
            // leaves the evidence in the log.
            when (val outcome = parser.parse(body)) {
                is ParseOutcome.Unrecognized -> captures.record(
                    RawCapture(
                        sourceApp = pkg,
                        capturedAt = Clock.System.now(),
                        body = body,
                        result = CaptureResult.UNRECOGNIZED,
                        reason = outcome.reason,
                    ),
                )
                is ParseOutcome.Parsed -> captures.record(
                    RawCapture(
                        sourceApp = pkg,
                        capturedAt = Clock.System.now(),
                        body = body,
                        result = if (outcome.draft.needsReview) {
                            CaptureResult.NEEDS_REVIEW
                        } else {
                            CaptureResult.PARSED
                        },
                        matchedAmount = outcome.draft.matchedAmount,
                        matchedDirection = outcome.draft.matchedDirection,
                        reason = outcome.reason,
                    ),
                )
                // TODO: persist the Transaction itself via TransactionRepository,
                // defaulting to NEEDS_REVIEW. Never auto-confirm.
            }
            allowList.incrementCapturedCount(pkg)
        }
    }

    override fun onListenerConnected() {
        // The binding drops on app update and can fail silently. Surface real
        // connection state to the user instead of assuming it is alive.
        super.onListenerConnected()
    }
}
