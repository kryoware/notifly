package ph.notifly.domain.model

import kotlin.time.Instant

enum class CaptureResult { PARSED, NEEDS_REVIEW, UNRECOGNIZED, IGNORED }

/**
 * One notification as it arrived, plus what the parser made of it.
 * Backs the in-app notification log (Settings → Troubleshooting).
 *
 * PRIVACY: [body] is sensitive. It is written only when the user has
 * "Keep raw text on device" enabled, is purged after [RETENTION_HOURS],
 * and must never be attached to a sync payload or a log statement.
 */
data class RawCapture(
    val id: Long = 0,
    val sourceApp: String,
    val capturedAt: Instant,
    val body: String?,              // null once redacted or purged
    val result: CaptureResult,
    val matchedAmount: String? = null,
    val matchedDirection: String? = null,
    val reason: String,             // plain language, shown to the user
) {
    companion object { const val RETENTION_HOURS = 24 }
}
