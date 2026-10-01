package ph.notifly.data.local

import ph.notifly.domain.model.Account
import ph.notifly.domain.model.AccountType

suspend fun AppDatabase.seedTestAccounts() {
    ledgerDao().saveAccount(Account(1, "Maya", AccountType.WALLET))
    ledgerDao().saveAccount(Account(2, "MariBank", AccountType.BANK))
    ledgerDao().saveAccount(Account(3, "GCash", AccountType.WALLET))
    ledgerDao().seedCategories()
}
