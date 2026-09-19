package ph.notifly.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : CaptureRepository {

    override fun observeLog(filter: CaptureResult?): Flow<List<RawCapture>> =
        dao.observeLog(filter?.name).map { entities -> entities.map { it.toDomain() } }

    override suspend fun record(capture: RawCapture): Long =
        dao.record(capture.toEntity())

    override suspend fun clearLog() =
        dao.clearLog()

    override suspend fun redactBodies() =
        dao.redactBodies()

    override suspend fun purgeExpired() {
        val cutoff = clock.now().minus(RawCapture.RETENTION_HOURS.hours)
        dao.purgeExpired(cutoff.toEpochMilliseconds())
    }
}
