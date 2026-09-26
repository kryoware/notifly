package ph.notifly.domain.source

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.notifly.domain.repository.CaptureRepository
import ph.notifly.data.parser.NotificationParser
import ph.notifly.data.parser.ParseOutcome
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType
import kotlin.time.Clock
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NotificationTransactionSource(
    private val context: Context,
    private val captures: CaptureRepository,
    private val allowList: ph.notifly.domain.repository.AllowListRepository,
    private val parser: NotificationParser,
) : TransactionSource {
    override val id = "android.notification-listener"
    private val mutableConnection = MutableStateFlow("Disconnected")
    override val connection = mutableConnection.asStateFlow()
    override fun isAvailable(): Boolean = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners",
    ).orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).any {
        it.packageName == context.packageName
    }
    override fun observe() = captures.observeLog()
        .let { flow -> kotlinx.coroutines.flow.flow { flow.collect { rows -> rows.firstOrNull()?.let { emit(it) } } } }
    /**
     * Purges expired captures, then records nonblank notifications from allowed packages.
     * Rejected packages produce no log entry or content read. Parsed drafts remain awaiting review;
     * the repository may merge matching transfer legs. Only a new capture increments the app's count.
     * App-label lookup failures fall back to the package name; content, parser, and storage failures propagate.
     */
    override suspend fun capture(event: NotificationEvent) {
        captures.purgeExpired()
        if (!allowList.isAllowed(event.sourceApp)) {
            return
        }
        val sourceAppLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(event.sourceApp, 0),
            ).toString()
        }.getOrDefault(event.sourceApp)
        val content = event.readContent()
        val body = listOf(content.title, content.text).filter { it.isNotBlank() }.joinToString(" — ")
        if (body.isBlank()) return
        val now = Clock.System.now()
        val fingerprint = digest("${event.key}\u0000$body")
        val otherFinanceApps = allowList.observeAll().first().filter { it.finance && it.packageName != event.sourceApp }.map { it.label }
        val id = when (val result = parser.parse(body, otherFinanceApps)) {
            is ParseOutcome.Unrecognized -> captures.record(RawCapture(sourceApp = sourceAppLabel, capturedAt = now, body = body,
                result = CaptureResult.UNRECOGNIZED, reason = result.reason, fingerprint = fingerprint))
            is ParseOutcome.Parsed -> {
                val draft = result.draft
                captures.recordParsed(RawCapture(sourceApp = sourceAppLabel, capturedAt = now, body = body,
                    result = if (draft.needsReview) CaptureResult.NEEDS_REVIEW else CaptureResult.PARSED,
                    matchedAmount = draft.matchedAmount, matchedDirection = draft.matchedDirection,
                    reason = result.reason, fingerprint = fingerprint), Transaction(
                    title = draft.merchant ?: "Payment from $sourceAppLabel", amountMinor = draft.amountMinor,
                    currency = draft.currency, type = draft.type, status = TransactionStatus.NEEDS_REVIEW,
                    category = if (draft.type == TransactionType.TRANSFER) "Transfer" else "Other", occurredAt = now, sourceApp = event.sourceApp, captureId = null))
            }
        }
        if (id != -1L) allowList.incrementCapturedCount(event.sourceApp)
    }
    fun connected(value: Boolean) { mutableConnection.value = if (value) "Connected" else "Disconnected" }
    fun storageError() { mutableConnection.value = "Capture failed — check device storage, then reconnect" }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
