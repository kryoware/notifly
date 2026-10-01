package ph.notifly.data.parser

import android.content.Context
import ph.notifly.domain.model.TransactionType

/** Bundled inference stays on the notification service's IO dispatcher. */
class ModelNotificationParser(context: Context, private val rules: NotificationParser) {
    private val classifier by lazy {
        NotificationClassifier(context.assets.open("notification_model.json").bufferedReader().use { it.readText() })
    }

    fun parse(source: String, body: String, financeApps: Collection<String> = emptyList()): ParseOutcome {
        if (body.isBlank()) return ParseOutcome.Unrecognized("Empty notification body.")
        val input = if (source.isBlank()) body else "$source: $body"
        if (input.codePointCount(0, input.length) > 4096) {
            return ParseOutcome.Unrecognized("Notification exceeds the on-device model's input limit.")
        }
        // Ownership stays unknown: a direction prediction cannot establish a self-transfer.
        val prediction = classifier.classify(source, body)
        val confident = prediction.confidence >= classifier.minConfidence
        if (confident && prediction.direction == "other") {
            return ParseOutcome.Unrecognized("On-device model identified a non-transaction notification.")
        }
        val hint = if (confident) when (prediction.direction) {
            "income" -> TransactionType.INCOME
            "expense" -> TransactionType.EXPENSE
            else -> null
        } else null
        val parsed = rules.parse(body, financeApps, hint)
        return if (!confident && parsed is ParseOutcome.Parsed) {
            parsed.copy(
                draft = parsed.draft.copy(directionConfidence = Confidence.LOW),
                reason = "Model confidence is low; rule-based suggestion needs review. ${parsed.reason}",
            )
        } else parsed
    }
}
