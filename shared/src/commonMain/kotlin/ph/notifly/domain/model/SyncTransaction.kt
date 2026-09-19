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
)

fun Transaction.toSyncTransaction(): SyncTransaction {
    require(status == TransactionStatus.CONFIRMED) { "Only confirmed transactions can sync" }
    require(amountMinor > 0 && title.isNotBlank())
    return SyncTransaction(id, title, amountMinor, currency, type, category, occurredAt.toEpochMilliseconds())
}
