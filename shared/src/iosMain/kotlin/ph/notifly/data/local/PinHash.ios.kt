package ph.notifly.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal actual fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
    val saltBytes = salt.asUByteArray()
    val out = UByteArray(PIN_HASH_BYTES)
    val status = saltBytes.usePinned { s ->
        out.usePinned { o ->
            CCKeyDerivationPBKDF(kCCPBKDF2.convert(), pin, pin.encodeToByteArray().size.convert(), s.addressOf(0), saltBytes.size.convert(),
                kCCPRFHmacAlgSHA256.convert(), PIN_ITERATIONS.convert(), o.addressOf(0), out.size.convert())
        }
    }
    check(status == kCCSuccess) { "PBKDF2 failed: $status" }
    return out.asByteArray()
}
