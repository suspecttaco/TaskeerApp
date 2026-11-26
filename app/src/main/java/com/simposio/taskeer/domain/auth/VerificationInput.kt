package com.simposio.taskeer.domain.auth

import java.net.URLDecoder

object VerificationInput {
    const val MAX_LENGTH = 512
    const val TOKEN_INVALID = "TOKEN_INVALID"
    private const val TOKEN_PARAM = "token"
    private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val WHITESPACE = Regex("\\s")

    // El token es opaco: solo se comprueba su forma externa, nunca su contenido.
    fun parseToken(raw: String): String? {
        val trimmed = raw.trim()
        val candidate = if (URL_SCHEME.containsMatchIn(trimmed)) tokenFromUrl(trimmed) else trimmed
        return candidate?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && !WHITESPACE.containsMatchIn(it) }
    }

    private fun tokenFromUrl(url: String): String? {
        val query = url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
        val value = query.split('&')
            .firstOrNull { it.substringBefore('=') == TOKEN_PARAM && it.contains('=') }
            ?.substringAfter('=')
            ?: return null
        return try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
