package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.domain.source.NotificationTransactionSource
import ph.notifly.domain.source.TransactionSource

actual val androidModule: Module = module {
    single<TransactionSource> { NotificationTransactionSource(get()) }
}
