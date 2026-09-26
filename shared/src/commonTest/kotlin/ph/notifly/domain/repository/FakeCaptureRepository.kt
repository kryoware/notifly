package ph.notifly.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class FakeCaptureRepository(
    private val clock: Clock = Clock.System,
) : CaptureRepository {
    val transactions = FakeTransactionRepository()
    override suspend fun recordParsed(capture: RawCapture, transaction: ph.notifly.domain.model.Transaction): Long {
        val id = record(capture)
        transactions.upsert(transaction.copy(captureId = id))
        return id
    }

    private val store = MutableStateFlow<List<RawCapture>>(emptyList())
    private var nextId = 1L

    override fun observeLog(filter: CaptureResult?): Flow<List<RawCapture>> =
        store.map { list ->
            val filtered = list.filter { filter == null || it.result == filter }
            filtered.sortedByDescending { it.capturedAt }
        }

    override suspend fun record(capture: RawCapture): Long {
        val id = if (capture.id == 0L) nextId++ else capture.id
        val saved = capture.copy(id = id)
        store.update { it + saved }
        return id
    }

    override suspend fun clearLog() {
        store.value = emptyList()
    }

    override suspend fun redactBodies() {
        store.update { list -> list.map { it.copy(body = null) } }
    }

    override suspend fun purgeExpired() {
        val cutoff = clock.now().minus(RawCapture.RETENTION_HOURS.hours)
        store.update { list -> list.filter { it.capturedAt >= cutoff } }
    }
}
