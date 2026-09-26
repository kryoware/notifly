package ph.notifly.data.local

internal const val PIN_LENGTH = 6
internal const val PIN_ITERATIONS = 120_000
internal const val PIN_HASH_BYTES = 32

/** PBKDF2-HMAC-SHA256 of [pin] with [salt], [PIN_ITERATIONS] rounds, [PIN_HASH_BYTES] long. */
internal expect fun pbkdf2(pin: String, salt: ByteArray): ByteArray

/** Compares every byte when lengths match; unequal lengths return false immediately. */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
    a.size == b.size && a.indices.fold(0) { acc, i -> acc or (a[i].toInt() xor b[i].toInt()) } == 0
