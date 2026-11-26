package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError

object AuthRules {
    const val EMAIL_MAX = 254
    const val PASSWORD_MAX = 128
    const val EMAIL_INVALID = "EMAIL_INVALID"
    const val PASSWORD_REQUIRED = "PASSWORD_REQUIRED"
    const val PASSWORD_TOO_LONG = "PASSWORD_TOO_LONG"
    const val MOBILE_CLIENT_NOT_SUPPORTED = "MOBILE_CLIENT_NOT_SUPPORTED"
    const val ALREADY_SIGNED_IN = "ALREADY_SIGNED_IN"

    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_LOCKOUT_SECONDS = 900L
    private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalizedEmail(email: String): String = email.trim()

    // La contraseña no se recorta ni se normaliza: los espacios son parte de la credencial.
    fun validateCredentials(email: String, password: CharArray): DomainError.Validation? {
        val normalized = normalizedEmail(email)
        return when {
            normalized.length > EMAIL_MAX || !EMAIL_SHAPE.matches(normalized) ->
                DomainError.Validation(EMAIL_INVALID, "Escribe un correo válido")
            password.isEmpty() ->
                DomainError.Validation(PASSWORD_REQUIRED, "Escribe tu contraseña")
            password.size > PASSWORD_MAX ->
                DomainError.Validation(PASSWORD_TOO_LONG, "La contraseña es demasiado larga")
            else -> null
        }
    }

    fun retryMinutes(retryAfterSeconds: Long?): Int? =
        retryAfterSeconds?.let { maxOf(1L, (it + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt() }

    fun lockoutSeconds(retryAfterSeconds: Long?): Long =
        retryAfterSeconds?.coerceIn(1L, MAX_LOCKOUT_SECONDS) ?: 0L
}
