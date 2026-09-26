package ph.notifly.ui

import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

data class AccountBalance(val app: AllowedApp, val estimate: Long, val manual: ManualBalance?)

/**
 * Confirmed income minus spending captured from each finance app, on top of the balance the user
 * entered (only transactions after it count). Transfers record just one app, not which side the
 * money left, so they are left out.
 */
fun accountBalances(apps: List<AllowedApp>, rows: List<Transaction>, manual: Map<String, ManualBalance>) =
    apps.filter { it.finance }.map { app ->
        val base = manual[app.packageName]
        val net = rows.filter { it.sourceApp == app.packageName && it.status == TransactionStatus.CONFIRMED &&
            (base == null || it.occurredAt > base.setAt) }
            .sumOf { when (it.type) {
                TransactionType.INCOME -> it.amountMinor
                TransactionType.EXPENSE -> -it.amountMinor
                TransactionType.TRANSFER -> 0L
            } }
        AccountBalance(app, (base?.minor ?: 0L) + net, base)
    }
