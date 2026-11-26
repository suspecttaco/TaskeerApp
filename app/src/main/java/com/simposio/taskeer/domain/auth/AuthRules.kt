package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError

object AuthRules {
    const val EMAIL_MAX = 254
    const val PASSWORD_MAX = 128
    const val DISPLAY_NAME_MIN = 2
    const val DISPLAY_NAME_MAX = 100
    const val PASSWORD_MIN = 8
    const val DISPLAY_NAME_INVALID = "DISPLAY_NAME_INVALID"
    const val PASSWORD_TOO_SHORT = "PASSWORD_TOO_SHORT"
    const val PASSWORD_MISMATCH = "PASSWORD_MISMATCH"
    const val EMAIL_INVALID = "EMAIL_INVALID"
    const val PASSWORD_REQUIRED = "PASSWORD_REQUIRED"
    const val PASSWORD_TOO_LONG = "PASSWORD_TOO_LONG"
    const val MOBILE_CLIENT_NOT_SUPPORTED = "MOBILE_CLIENT_NOT_SUPPORTED"
    const val ALREADY_SIGNED_IN = "ALREADY_SIGNED_IN"
    const val CODE_INVALID = "CODE_INVALID"
    const val NO_LOCAL_PASSWORD = "NO_LOCAL_PASSWORD"
    const val INVALID_GOOGLE_TOKEN = "INVALID_GOOGLE_TOKEN"
    const val GOOGLE_EMAIL_NOT_VERIFIED = "GOOGLE_EMAIL_NOT_VERIFIED"
    const val LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED = "LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED"

    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_LOCKOUT_SECONDS = 900L
    private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun normalizedEmail(email: String): String = email.trim()

    // La contraseña no se recorta ni se normaliza: los espacios son parte de la credencial.
    fun validateCredentials(email: String, password: CharArray): DomainError.Validation? {
        return when {
            !isEmailValid(email) -> emailInvalid()
            password.isEmpty() ->
                DomainError.Validation(PASSWORD_REQUIRED, "Escribe tu contraseña")
            password.size > PASSWORD_MAX ->
                DomainError.Validation(PASSWORD_TOO_LONG, "La contraseña es demasiado larga")
            else -> null
        }
    }

    fun validateRegistration(displayName: String, email: String, password: CharArray): DomainError.Validation? {
        val nameLength = displayName.trim().length
        return when {
            nameLength < DISPLAY_NAME_MIN || nameLength > DISPLAY_NAME_MAX ->
                DomainError.Validation(DISPLAY_NAME_INVALID, "El nombre debe tener entre 2 y 100 caracteres")
            !isEmailValid(email) -> emailInvalid()
            password.size < PASSWORD_MIN ->
                DomainError.Validation(PASSWORD_TOO_SHORT, "La contraseña debe tener al menos 8 caracteres")
            password.size > PASSWORD_MAX ->
                DomainError.Validation(PASSWORD_TOO_LONG, "La contraseña es demasiado larga")
            else -> null
        }
    }

    fun validateCurrentPassword(current: CharArray): DomainError.Validation? = when {
        current.isEmpty() -> DomainError.Validation(PASSWORD_REQUIRED, "Escribe tu contraseña actual")
        current.size > PASSWORD_MAX -> DomainError.Validation(PASSWORD_TOO_LONG, "La contraseña es demasiado larga")
        else -> null
    }

    fun validateNewPassword(newPassword: CharArray, confirmation: CharArray): DomainError.Validation? = when {
        newPassword.size < PASSWORD_MIN || newPassword.size > PASSWORD_MAX ->
            DomainError.Validation(PASSWORD_TOO_SHORT, "La contraseña debe tener entre 8 y 128 caracteres")
        !passwordsMatch(newPassword, confirmation) ->
            DomainError.Validation(PASSWORD_MISMATCH, "Las contraseñas no coinciden")
        else -> null
    }

    fun validateChangePassword(current: CharArray, newPassword: CharArray, confirmation: CharArray): DomainError.Validation? =
        validateCurrentPassword(current) ?: validateNewPassword(newPassword, confirmation)

    fun isEmailShapeValid(email: String): Boolean = isEmailValid(email)

    fun passwordsMatch(password: CharArray, confirmation: CharArray): Boolean = password.contentEquals(confirmation)

    private fun isEmailValid(email: String): Boolean {
        val normalized = normalizedEmail(email)
        return normalized.length <= EMAIL_MAX && EMAIL_SHAPE.matches(normalized)
    }

    private fun emailInvalid() = DomainError.Validation(EMAIL_INVALID, "Escribe un correo válido")

    fun retryMinutes(retryAfterSeconds: Long?): Int? =
        retryAfterSeconds?.let { maxOf(1L, (it + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt() }

    fun lockoutSeconds(retryAfterSeconds: Long?): Long =
        retryAfterSeconds?.coerceIn(1L, MAX_LOCKOUT_SECONDS) ?: 0L
}
