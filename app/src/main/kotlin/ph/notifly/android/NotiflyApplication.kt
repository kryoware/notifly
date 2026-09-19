package ph.notifly.android

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ph.notifly.di.androidModule
import ph.notifly.di.sharedModule

class NotiflyApplication : Application() {
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
    }
}
