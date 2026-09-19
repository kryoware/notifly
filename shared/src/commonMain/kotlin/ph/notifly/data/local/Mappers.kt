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
    sourceApp = sourceApp,
    captureId = captureId,
    note = note,
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
    sourceApp = sourceApp,
    captureId = captureId,
    note = note,
)

fun RawCaptureEntity.toDomain() = RawCapture(
    id = id,
    sourceApp = sourceApp,
    capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
    body = body,
    result = CaptureResult.valueOf(result),
    matchedAmount = matchedAmount,
    matchedDirection = matchedDirection,
    reason = reason,
)

fun RawCapture.toEntity() = RawCaptureEntity(
    id = id,
    sourceApp = sourceApp,
    capturedAtMillis = capturedAt.toEpochMilliseconds(),
    body = body,
    result = result.name,
    matchedAmount = matchedAmount,
    matchedDirection = matchedDirection,
    reason = reason,
)

fun AllowedAppEntity.toDomain() = AllowedApp(
    packageName = packageName,
    label = label,
    kind = kind,
    listening = listening,
    capturedCount = capturedCount,
)

fun AllowedApp.toEntity() = AllowedAppEntity(
    packageName = packageName,
    label = label,
    kind = kind,
    listening = listening,
    capturedCount = capturedCount,
)
