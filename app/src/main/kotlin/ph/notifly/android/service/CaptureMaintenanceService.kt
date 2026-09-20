package ph.notifly.android.service

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite
import ph.notifly.domain.repository.CaptureRepository

/** Native scheduled cleanup also runs while the UI is closed, subject to Android scheduling. */
class CaptureMaintenanceService : JobService() {
    private val captures: CaptureRepository by inject()
    private val preferences: AppPreferences by inject()
    private val reporter: ErrorReporter by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val retry = try {
                withContext(Dispatchers.IO) {
                    if (!preferences.keepRawText.first()) captures.redactBodies()
                    captures.purgeExpired()
                }
                false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                reporter.report(e, ErrorSite.CAPTURE_MAINTENANCE)
                true
            }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
