package ph.notifly.data.local

import kotlin.time.Instant
import ph.notifly.domain.model.AllowedApp
import ph.notifly.domain.model.CaptureResult
import ph.notifly.domain.model.RawCapture
import ph.notifly.domain.model.Transaction
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    title = title,
    amountMinor = amountMinor,
    currency = currency,
    type = TransactionType.valueOf(type),
    status = TransactionStatus.valueOf(status),
    category = category,
    occurredAt = Instant.fromEpochMilliseconds(occurredAtMillis),
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
    sourceApp = sourceApp,
    captureId = captureId,
    note = note,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    fromApp = fromApp,
    toApp = toApp,
    feeMinor = feeMinor,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    title = title,
    amountMinor = amountMinor,
    currency = currency,
    type = type.name,
    status = status.name,
    category = category,
    occurredAtMillis = occurredAt.toEpochMilliseconds(),
    createdAtMillis = createdAt.toEpochMilliseconds(),
    sourceApp = sourceApp,
    captureId = captureId,
    note = note,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    fromApp = fromApp,
    toApp = toApp,
    feeMinor = feeMinor,
)

fun RawCaptureEntity.toDomain() = RawCapture(
    fingerprint = fingerprint,
    id = id,
    sourceApp = sourceApp,
    capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
    body = body,
    result = CaptureResult.valueOf(result),
    matchedAmount = matchedAmount,
    matchedDirection = matchedDirection,
    reason = reason,
    extras = extras?.let(::decodeExtras).orEmpty(),
)

fun RawCapture.toEntity() = RawCaptureEntity(
    fingerprint = fingerprint,
    id = id,
    sourceApp = sourceApp,
    capturedAtMillis = capturedAt.toEpochMilliseconds(),
    body = body,
    result = result.name,
    matchedAmount = matchedAmount,
    matchedDirection = matchedDirection,
    reason = reason,
    extras = extras.takeIf { it.isNotEmpty() }?.let(::encodeExtras),
)

// One "key\tvalue" per line; escaping keeps raw tabs/newlines out so split is safe.
private fun encodeExtras(extras: Map<String, String>) =
    extras.entries.joinToString("\n") { (key, value) -> "${key.escapeField()}\t${value.escapeField()}" }

private fun decodeExtras(value: String) = value.split("\n").mapNotNull { line ->
    line.split("\t").takeIf { it.size == 2 }?.let { (key, field) -> key.unescapeField() to field.unescapeField() }
}.toMap()

private fun String.escapeField() = replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

private fun String.unescapeField() = Regex("\\\\(.)").replace(this) {
    when (val c = it.groupValues[1]) { "n" -> "\n"; "t" -> "\t"; else -> c }
}

fun AllowedAppEntity.toDomain() = AllowedApp(
    packageName = packageName,
    label = label,
    kind = kind,
    listening = listening,
    capturedCount = capturedCount,
    finance = finance,
)

fun AllowedApp.toEntity() = AllowedAppEntity(
    packageName = packageName,
    label = label,
    kind = kind,
    listening = listening,
    capturedCount = capturedCount,
    finance = finance,
)
