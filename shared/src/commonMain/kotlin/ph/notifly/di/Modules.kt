package ph.notifly.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ph.notifly.data.parser.NotificationParser

val sharedModule: Module = module {
    single { NotificationParser() }
    // TODO: single<TransactionRepository> { ... }
    // TODO: single<CaptureRepository> { ... }
    // TODO: single<AllowListRepository> { ... }
}

/** Platform wiring: DB builder, TransactionSource, Context-dependent pieces. */
expect val androidModule: Module
