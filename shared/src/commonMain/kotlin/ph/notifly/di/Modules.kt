package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.data.local.AppDatabase
import ph.notifly.data.parser.NotificationParser
import ph.notifly.data.repository.AllowListRepositoryImpl
import ph.notifly.data.repository.CaptureRepositoryImpl
import ph.notifly.data.repository.TransactionRepositoryImpl
import ph.notifly.domain.repository.AllowListRepository
import ph.notifly.domain.repository.CaptureRepository
import ph.notifly.domain.repository.TransactionRepository
import ph.notifly.domain.model.RawCapture
import kotlin.time.Duration.Companion.hours

val sharedModule: Module = module {
    single { NotificationParser() }
    single<ph.notifly.domain.repository.LedgerRepository> { ph.notifly.data.repository.LedgerRepositoryImpl(get<AppDatabase>().ledgerDao(), get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get<AppDatabase>().transactionDao(), get()) }
    single<CaptureRepository> {
        val hours = if (getProperty("debug", false)) RawCapture.DEBUG_RETENTION_HOURS else RawCapture.RETENTION_HOURS
        CaptureRepositoryImpl(get<AppDatabase>().rawCaptureDao(), preferences = get(), retention = hours.hours)
    }
    single<AllowListRepository> { AllowListRepositoryImpl(get<AppDatabase>().allowedAppDao()) }
}

/** Platform wiring: DB builder, TransactionSource, Context-dependent pieces. */
expect val androidModule: Module
