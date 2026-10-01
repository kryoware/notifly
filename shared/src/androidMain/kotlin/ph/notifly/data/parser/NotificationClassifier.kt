package ph.notifly.data.parser

import org.json.JSONObject
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Load once from a bundled asset. No network or external ML runtime. */
class NotificationClassifier(modelJson: String) {
    data class Prediction(
        val label: String?,
        val direction: String?,
        val confidence: Double,
        val reason: String?,
        val probabilities: Map<String, Double>
    ) {
        val needsReview: Boolean get() = label == null
    }

    private val labels: List<String>
    private val vocab: Map<String, Int>
    private val idf: DoubleArray
    private val coefficients: Array<ByteArray>
    private val scales: DoubleArray
    private val intercept: DoubleArray
    private val ngramRange: IntRange
    private val maxChars: Int
    private val threshold: Double
    val demoOnly: Boolean
    val minConfidence: Double get() = threshold
    private val numbers = Regex("[0-9]+(?:[.,][0-9]+)*")
    // Match Python Unicode whitespace, including its four additional control characters.
    private val whitespace = Regex("[\\t-\\r \\u001c-\\u001f\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]+")

    init {
        val model = JSONObject(modelJson)
        require(model.getInt("format_version") == 1) { "Unsupported model format" }
        labels = model.getJSONArray("labels").let { values -> List(values.length()) { values.getString(it) } }
        require(labels.toSet() == setOf("expense", "income", "other") && labels.size == 3)
        val vocabulary = model.getJSONObject("vocab")
        vocab = vocabulary.keys().asSequence().associateWith { vocabulary.getInt(it) }
        idf = model.getJSONArray("idf").let { values -> DoubleArray(values.length()) { values.getDouble(it) } }
        val rows = model.getJSONArray("coef_int8")
        coefficients = Array(labels.size) { row ->
            val values = rows.getJSONArray(row)
            require(values.length() == idf.size)
            ByteArray(values.length()) { index ->
                val value = values.getInt(index)
                require(value in -127..127)
                value.toByte()
            }
        }
        scales = model.getJSONArray("coef_scale").let { values -> DoubleArray(labels.size) { values.getDouble(it) } }
        intercept = model.getJSONArray("intercept").let { values -> DoubleArray(labels.size) { values.getDouble(it) } }
        val range = model.getJSONArray("ngram_range")
        ngramRange = range.getInt(0)..range.getInt(1)
        require(ngramRange == 3..5 && vocab.size == idf.size && vocab.values.toSet() == idf.indices.toSet())
        maxChars = model.getInt("max_chars")
        threshold = model.getDouble("min_confidence")
        require(maxChars == 4096 && threshold in 0.0..1.0)
        demoOnly = model.getBoolean("demo_only")
    }

    /** Observed source/text only; parser outputs are never model features. */
    fun classify(
        source: String,
        text: String,
        ownAccountTransfer: Boolean? = null,
        minConfidence: Double = threshold
    ): Prediction {
        require(whitespace.replace(text, " ").trim().isNotEmpty()) { "Notification text must be nonempty" }
        val app = whitespace.replace(source, " ").trim()
        return classify(if (app.isEmpty()) text else "$app: $text", ownAccountTransfer, minConfidence)
    }

    /** null ownership means unknown; true requires verified ownership of BOTH accounts. */
    fun classify(
        text: String,
        ownAccountTransfer: Boolean? = null,
        minConfidence: Double = threshold
    ): Prediction {
        require(minConfidence in 0.0..1.0) { "Confidence threshold must be between 0 and 1" }
        require(text.codePointCount(0, text.length) <= maxChars) { "Notification is too long" }
        val normalized = whitespace.replace(numbers.replace(text.lowercase(Locale.ROOT), "0"), " ").trim()
        require(normalized.isNotEmpty()) { "Notification text must be nonempty" }
        val counts = mutableMapOf<Int, Int>()
        for (token in normalized.split(' ')) {
            val word = " $token "
            val length = word.codePointCount(0, word.length)
            val offsets = IntArray(length + 1)
            for (i in 0 until length) offsets[i + 1] = offsets[i] + Character.charCount(word.codePointAt(offsets[i]))
            for (n in ngramRange) {
                val last = if (length <= n) 0 else length - n
                for (start in 0..last) {
                    val gram = word.substring(offsets[start], offsets[minOf(start + n, length)])
                    val index = vocab[gram] ?: continue
                    counts[index] = (counts[index] ?: 0) + 1
                }
                if (length <= n) break
            }
        }
        if (counts.isEmpty()) return Prediction(null, null, 0.0, "unknown_vocabulary", emptyMap())
        val values = counts.mapValues { (index, count) -> (1 + ln(count.toDouble())) * idf[index] }
        val norm = sqrt(values.values.sumOf { it * it })
        val scores = DoubleArray(labels.size) { row ->
            intercept[row] + scales[row] * values.entries.sumOf { (index, value) -> coefficients[row][index].toInt() * value / norm }
        }
        val maximum = scores.maxOrNull()!!
        val exps = scores.map { exp(it - maximum) }
        val total = exps.sum()
        val probabilities = labels.indices.associate { labels[it] to exps[it] / total }
        val index = scores.indices.maxByOrNull { scores[it] }!!
        val direction = labels[index]
        val confidence = probabilities.getValue(direction)
        val reason = when {
            confidence < minConfidence -> "low_confidence"
            direction != "other" && ownAccountTransfer == null -> "unknown_ownership"
            else -> null
        }
        val label = when {
            reason != null -> null
            direction != "other" && ownAccountTransfer == true -> "transfer"
            else -> direction
        }
        return Prediction(label, direction, confidence, reason, probabilities)
    }
}

