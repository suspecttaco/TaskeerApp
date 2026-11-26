package com.simposio.taskeer.data.auth.google

import java.security.MessageDigest
import java.security.SecureRandom

// `raw` va en claro al backend; Google recibe `sha256Hex` y lo incrusta en el token, de modo que el servidor puede ligarlos.
class GoogleNonce(val raw: String, val sha256Hex: String) {
    override fun toString(): String = "GoogleNonce(redacted)"

    companion object {
        private const val BYTES = 32
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun generate(random: SecureRandom = SecureRandom()): GoogleNonce {
            val bytes = ByteArray(BYTES).also(random::nextBytes)
            val raw = base64Url(bytes)
            return GoogleNonce(raw, sha256Hex(raw))
        }

        internal fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        // `java.util.Base64` exige API 26 y minSdk es 24.
        internal fun base64Url(bytes: ByteArray): String {
            val out = StringBuilder((bytes.size * 4 + 2) / 3)
            var index = 0
            while (index < bytes.size) {
                val remaining = bytes.size - index
                val b0 = bytes[index].toInt() and 0xFF
                val b1 = if (remaining > 1) bytes[index + 1].toInt() and 0xFF else 0
                val b2 = if (remaining > 2) bytes[index + 2].toInt() and 0xFF else 0
                out.append(ALPHABET[b0 shr 2])
                out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
                if (remaining > 1) out.append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                if (remaining > 2) out.append(ALPHABET[b2 and 0x3F])
                index += 3
            }
            return out.toString()
        }
    }
}
