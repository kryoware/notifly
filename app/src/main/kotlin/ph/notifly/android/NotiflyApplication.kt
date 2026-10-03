package ph.notifly.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val launches = MutableStateFlow(0L)

    /** An unchanged selection is still reconciled on the next Activity launch after a PM failure. */
    fun reconcileLauncherIcon() { launches.value += 1 }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NotiflyApplication)
            properties(mapOf("debug" to BuildConfig.DEBUG))
            modules(sharedModule, androidModule)
        }
        val scheduler = getSystemService(android.app.job.JobScheduler::class.java)
        scheduler.schedule(android.app.job.JobInfo.Builder(1,
            android.content.ComponentName(this, ph.notifly.android.service.CaptureMaintenanceService::class.java))
            .setPeriodic(java.util.concurrent.TimeUnit.HOURS.toMillis(6))
            .setPersisted(true)
            .build())
        // ponytail: JobScheduler can't fire at a fixed hour. Switch to AlarmManager inexact at 9am if users want a fixed time.
        scheduler.schedule(android.app.job.JobInfo.Builder(2,
            android.content.ComponentName(this, ph.notifly.android.service.BillReminderService::class.java))
            .setPeriodic(java.util.concurrent.TimeUnit.HOURS.toMillis(12))
            .setPersisted(true)
            .build())

        scope.launch {
            preferences.crashReporting.collect { enabled ->
                CrashReporting.setEnabled(enabled, this@NotiflyApplication, BuildConfig.SENTRY_DSN)
            }
        }
        scope.launch {
            val launcher = LauncherIcons(packageManager, packageName)
            preferences.appearance.combine(launches) { appearance, _ -> appearance }
                .collect { launcher.apply(it) }
        }
    }
}
