package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture

interface CaptureRepository {
    fun observeLog(filter: CaptureResult? = null): Flow<List<RawCapture>>
    suspend fun record(capture: RawCapture): Long
    suspend fun clearLog()
    /** Blanks [RawCapture.body] in place when the user revokes retention. */
    suspend fun redactBodies()
    suspend fun purgeExpired()
}
