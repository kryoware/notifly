package ph.notifly.domain.source

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.RawCapture

/**
 * THE platform boundary.
 *
 * Android  — wraps NotificationListenerService.
 * iOS      — has no equivalent API. iOS must supply captures from a bank
 *            aggregator, file import, or manual entry instead.
 *
 * Everything downstream (parser, review workflow, ledger, sync, UI) is shared
 * and must not know which implementation produced a capture.
 */
interface TransactionSource {
    val id: String
    fun isAvailable(): Boolean
    fun observe(): Flow<RawCapture>
}
