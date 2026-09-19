package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture

interface CaptureRepository {
    fun observeLog(filter: CaptureResult? = null): Flow<List<RawCapture>>
    suspend fun record(capture: RawCapture): Long
    /** Atomically save the capture and its unconfirmed draft; -1 means already captured. */
    suspend fun recordParsed(capture: RawCapture, transaction: ph.notifly.domain.model.Transaction): Long
    suspend fun clearLog()
    /** Blanks [RawCapture.body] in place when the user revokes retention. */
    suspend fun redactBodies()
    suspend fun purgeExpired()
}
