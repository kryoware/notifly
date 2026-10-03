package ph.notifly.domain.model

/** Explicit allow-list for cloud fields. Notification bodies, capture ids and notes are absent. */
data class SyncTransaction(
    val id: Long,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val type: TransactionType,
    val category: String,
    val occurredAtMillis: Long,
    val feeMinor: Long = 0,
)

fun Transaction.toSyncTransaction(): SyncTransaction {
    require(status == TransactionStatus.CONFIRMED) { "Only confirmed transactions can sync" }
    require(currency == "PHP") { "Only PHP transactions can sync" }
    require(amountMinor > 0 && title.isNotBlank())
    require(feeMinor >= 0 && amountMinor <= Long.MAX_VALUE - feeMinor)
    require(type == TransactionType.TRANSFER || feeMinor == 0L)
    require(accountId > 0 && (type != TransactionType.TRANSFER || (toAccountId != null && toAccountId > 0 && toAccountId != accountId)))
    return SyncTransaction(id, title, amountMinor, currency, type, category, occurredAt.toEpochMilliseconds(), feeMinor)
}
