package com.simposio.taskeer.domain.auth

import java.util.Locale

const val AVATAR_PALETTE_SIZE: Int = 8

private val WHITESPACE = Regex("[\\s\\p{Z}]+")

private const val FNV_OFFSET_BASIS = 0x811C9DC5.toInt()
private const val FNV_PRIME = 0x01000193

fun avatarInitials(displayName: String, email: String): String? {
    val words = displayName.trim().split(WHITESPACE).mapNotNull(::firstLetterOrDigit)
    if (words.isNotEmpty()) {
        return if (words.size == 1) words.first() else words.first() + words.last()
    }
    return firstLetterOrDigit(email.substringBefore('@'))
}

// FNV-1a explícito: `String.hashCode()` no está garantizado entre versiones y el color de identidad debe ser estable.
fun avatarColorIndex(displayName: String, email: String, paletteSize: Int = AVATAR_PALETTE_SIZE): Int {
    require(paletteSize > 0) { "paletteSize debe ser positivo" }
    val seed = normalizedSeed(displayName).ifEmpty { normalizedSeed(email) }
    if (seed.isEmpty()) return 0
    var hash = FNV_OFFSET_BASIS
    for (byte in seed.toByteArray(Charsets.UTF_8)) {
        hash = (hash xor (byte.toInt() and 0xFF)) * FNV_PRIME
    }
    return Math.floorMod(hash, paletteSize)
}

private fun normalizedSeed(value: String): String =
    value.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

private fun firstLetterOrDigit(word: String): String? {
    var index = 0
    while (index < word.length) {
        val codePoint = word.codePointAt(index)
        if (Character.isLetterOrDigit(codePoint)) {
            val upper = String(Character.toChars(codePoint)).uppercase(Locale.ROOT)
            return String(Character.toChars(upper.codePointAt(0)))
        }
        index += Character.charCount(codePoint)
    }
    return null
}
