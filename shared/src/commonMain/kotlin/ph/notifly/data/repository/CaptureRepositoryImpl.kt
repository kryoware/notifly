package ph.notifly.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.Transaction
import ph.notifly.data.local.RawCaptureDao
import ph.notifly.data.local.toDomain
import ph.notifly.data.local.toEntity
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import ph.notifly.domain.repository.CaptureRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class CaptureRepositoryImpl(
    private val dao: RawCaptureDao,
    private val clock: Clock = Clock.System,
    private val preferences: AppPreferences? = null,
) : CaptureRepository {
    private val writeLock = Mutex()

    /**
     * Observes captures matching [filter] (all results when null), excluding IGNORED entries.
     * Bodies are hidden unless raw-text retention is currently enabled.
     */
    override fun observeLog(filter: CaptureResult?): Flow<List<RawCapture>> =
        kotlinx.coroutines.flow.combine(dao.observeLog(filter?.name), preferences?.keepRawText ?: kotlinx.coroutines.flow.flowOf(false)) { entities, keep ->
            entities.filter { it.result != CaptureResult.IGNORED.name }
                .map { entity -> entity.toDomain().let { if (keep) it else it.copy(body = null) } }
        }

    override suspend fun record(capture: RawCapture): Long = writeLock.withLock {
        dao.recordOnce(retained(capture).toEntity())
    }

    override suspend fun recordParsed(capture: RawCapture, transaction: Transaction): Long = writeLock.withLock {
        dao.recordParsed(retained(capture).toEntity(), transaction.toEntity())
    }

    private suspend fun retained(capture: RawCapture) =
        if (preferences?.keepRawText?.first() == true) capture else capture.copy(body = null)

    override suspend fun clearLog() =
        dao.clearLog()

    override suspend fun redactBodies() = writeLock.withLock { dao.redactBodies() }

    override suspend fun purgeExpired() {
        val cutoff = clock.now().minus(RawCapture.RETENTION_HOURS.hours)
        dao.purgeExpired(cutoff.toEpochMilliseconds())
    }
}
