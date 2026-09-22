package ph.notifly.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import ph.notifly.data.diagnostics.CrashReporting
import ph.notifly.data.local.AppPreferences
import ph.notifly.di.androidModule
import ph.notifly.di.sharedModule

class NotiflyApplication : Application(), KoinComponent {
    private val preferences: AppPreferences by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NotiflyApplication)
            modules(sharedModule, androidModule)
        }
        val scheduler = getSystemService(android.app.job.JobScheduler::class.java)
        scheduler.schedule(android.app.job.JobInfo.Builder(1,
            android.content.ComponentName(this, ph.notifly.android.service.CaptureMaintenanceService::class.java))
            .setPeriodic(java.util.concurrent.TimeUnit.HOURS.toMillis(6))
            .setPersisted(true)
            .build())

        scope.launch {
            preferences.crashReporting.collect { enabled ->
                CrashReporting.setEnabled(enabled, this@NotiflyApplication, BuildConfig.SENTRY_DSN)
            }
        }
    }
}
