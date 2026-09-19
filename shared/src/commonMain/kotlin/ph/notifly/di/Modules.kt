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

val sharedModule: Module = module {
    single { NotificationParser() }
    single<TransactionRepository> { TransactionRepositoryImpl(get<AppDatabase>().transactionDao()) }
    single<CaptureRepository> { CaptureRepositoryImpl(get<AppDatabase>().rawCaptureDao()) }
    single<AllowListRepository> { AllowListRepositoryImpl(get<AppDatabase>().allowedAppDao()) }
}

/** Platform wiring: DB builder, TransactionSource, Context-dependent pieces. */
expect val androidModule: Module
