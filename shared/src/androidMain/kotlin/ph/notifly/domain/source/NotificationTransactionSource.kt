package ph.notifly.domain.source

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.notifly.domain.repository.CaptureRepository
import ph.notifly.data.parser.ModelNotificationParser
import ph.notifly.data.parser.ParseOutcome
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NotificationTransactionSource(
    private val context: Context,
    private val captures: CaptureRepository,
    private val allowList: ph.notifly.domain.repository.AllowListRepository,
    private val parser: ModelNotificationParser,
    private val ledger: ph.notifly.domain.repository.LedgerRepository,
    private val bills: ph.notifly.domain.repository.BillRepository,
    private val billParser: ph.notifly.data.parser.BillReminderParser,
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
        ledger.initialize()
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
        // Before the model parser: it would class a due notice as "other" and drop it.
        val today = kotlinx.datetime.TimeZone.currentSystemDefault().let { now.toLocalDateTime(it).date }
        billParser.parse(sourceAppLabel, body, today)?.let { found ->
            val due = found.dueOn.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + found.dueOn.day
            val captured = captures.record(RawCapture(sourceApp = sourceAppLabel, capturedAt = now, body = body, extras = content.extras,
                result = CaptureResult.BILL, reason = "Bill reminder: due $due. Review it in Bills.", fingerprint = fingerprint))
            if (captured != -1L) {
                bills.recordDetected(ph.notifly.domain.model.Bill(name = found.name, amountMinor = found.amountMinor, startsOn = found.dueOn,
                    repeat = ph.notifly.domain.model.BillRepeat.ONCE, status = TransactionStatus.NEEDS_REVIEW, detected = true,
                    sourceApp = event.sourceApp, createdAt = now))
                allowList.incrementCapturedCount(event.sourceApp)
            }
            return
        }
        val otherFinanceApps = allowList.observeAll().first().filter { it.finance && it.packageName != event.sourceApp }.map { it.label }
        val id = when (val result = parser.parse(sourceAppLabel, body, otherFinanceApps)) {
            is ParseOutcome.Unrecognized -> captures.record(RawCapture(sourceApp = sourceAppLabel, capturedAt = now, body = body, extras = content.extras,
                result = CaptureResult.UNRECOGNIZED, reason = result.reason, fingerprint = fingerprint))
            is ParseOutcome.Parsed -> {
                val draft = result.draft
                val accounts = ledger.observeAccounts().first()
                val (from, to) = ph.notifly.domain.model.resolveTransactionAccounts(accounts, event.sourceApp, body, draft.type, draft.inbound)
                val account = if (draft.type == TransactionType.TRANSFER && draft.inbound == true) to else from
                val reason = if (account == null) "Choose an account before creating this transaction. ${result.reason}" else result.reason
                captures.recordDraft(RawCapture(sourceApp = sourceAppLabel, capturedAt = now, body = body, extras = content.extras,
                    result = CaptureResult.NEEDS_REVIEW, matchedAmount = draft.matchedAmount,
                    matchedDirection = draft.matchedDirection, reason = reason, fingerprint = fingerprint),
                    ph.notifly.domain.model.CapturedDraft(title = draft.merchant ?: "Payment from $sourceAppLabel",
                        amountMinor = draft.amountMinor, type = draft.type, inbound = draft.inbound,
                        occurredAt = now, sourceApp = event.sourceApp,
                        accountId = if (draft.type == TransactionType.TRANSFER && from != null && to != null) from.id else account?.id,
                        toAccountId = to?.id.takeIf { draft.type == TransactionType.TRANSFER && from != null && to != null },
                        accountHint = Regex("(?i)(?:ending(?: in)?|card|account)\\s*[:#*xX. -]*([0-9]{4,})")
                            .find(body)?.groupValues?.get(1)?.takeLast(4)))
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
