package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.data.local.AppDatabase
import ph.notifly.data.local.getDatabaseBuilder
import ph.notifly.data.local.getRoomDatabase
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.source.IosTransactionSource
import ph.notifly.domain.source.TransactionSource

actual val androidModule: Module = module {
    single { ph.notifly.data.local.appPreferences() }
    single<TransactionSource> { IosTransactionSource() }
    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single<ErrorReporter> { ErrorReporter.None }
}
