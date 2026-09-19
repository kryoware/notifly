package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.domain.source.IosTransactionSource
import ph.notifly.domain.source.TransactionSource

actual val androidModule: Module = module {
    single<TransactionSource> { IosTransactionSource() }
}
