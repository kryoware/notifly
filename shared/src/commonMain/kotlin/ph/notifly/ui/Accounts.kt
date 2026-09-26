package ph.notifly.ui

import ph.notifly.data.local.ManualBalance
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

data class AccountBalance(val app: AllowedApp, val estimate: Long, val manual: ManualBalance?)

/**
 * Confirmed income minus spending captured from each finance app, on top of the balance the user
 * entered (only transactions after it count). A transfer moves money out of its `fromApp` and into
 * its `toApp`; an end that is not known leaves that account alone.
 */
fun accountBalances(apps: List<AllowedApp>, rows: List<Transaction>, manual: Map<String, ManualBalance>) =
    apps.filter { it.finance }.map { app ->
        val base = manual[app.packageName]
        val pkg = app.packageName
        val net = rows.filter { it.status == TransactionStatus.CONFIRMED && (base == null || it.occurredAt > base.setAt) }
            .sumOf { when {
                it.type == TransactionType.TRANSFER -> when (pkg) {
                    it.toApp -> it.amountMinor
                    it.fromApp -> -it.amountMinor
                    else -> 0L
                }
                it.sourceApp != pkg -> 0L
                it.type == TransactionType.INCOME -> it.amountMinor
                else -> -it.amountMinor
            } }
        AccountBalance(app, (base?.minor ?: 0L) + net, base)
    }
