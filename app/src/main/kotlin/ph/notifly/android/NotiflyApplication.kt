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
    }
}
