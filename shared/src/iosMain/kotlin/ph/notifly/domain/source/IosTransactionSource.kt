package ph.notifly.domain.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ph.notifly.domain.model.RawCapture

/**
 * iOS has NO API for reading other apps' notifications. A Notification Service
 * Extension only sees this app's own pushes, and SMS is inaccessible too.
 *
 * So iOS captures must come from elsewhere — a bank aggregator (Brankas or
 * Salt Edge cover PH), file import, or manual entry. Until one of those is
 * built, this source reports unavailable and emits nothing. That is correct
 * behaviour, not a stub to be "fixed" by faking captures.
 */
class IosTransactionSource : TransactionSource {
    override val id: String = "ios.unavailable"
    override fun isAvailable(): Boolean = false
    override fun observe(): Flow<RawCapture> = emptyFlow()
}
