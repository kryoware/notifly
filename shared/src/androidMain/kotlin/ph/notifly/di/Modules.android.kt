package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.data.local.AppDatabase
import ph.notifly.data.local.getDatabaseBuilder
import ph.notifly.data.local.getRoomDatabase
import ph.notifly.domain.source.NotificationTransactionSource
import ph.notifly.domain.source.TransactionSource

actual val androidModule: Module = module {
    single { ph.notifly.data.local.InstalledApps(get(), get()) }
    single { ph.notifly.data.local.appPreferences(get()) }
    single<TransactionSource> { NotificationTransactionSource(get(), get(), get(), get()) }
    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder(get())) }
}
