package com.simposio.taskeer.ui.passwordreset

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface ResetPasswordFailure {
    data class Inline(val error: ResetPasswordError) : ResetPasswordFailure

    data object NotSupported : ResetPasswordFailure
}

// Nunca se muestra `error.message`. Todo 400 del servidor es el mismo resultado para no distinguir correo de código.
fun resetPasswordFailureFor(error: DomainError): ResetPasswordFailure = when (error) {
    is DomainError.Validation -> ResetPasswordFailure.Inline(ResetPasswordError.INCORRECT)
    is DomainError.RateLimited -> ResetPasswordFailure.Inline(ResetPasswordError.RATE_LIMITED)
    is DomainError.Network -> ResetPasswordFailure.Inline(ResetPasswordError.NETWORK)
    is DomainError.Server -> ResetPasswordFailure.Inline(ResetPasswordError.SERVER)
    is DomainError.Forbidden ->
        if (error.code == AuthRules.MOBILE_CLIENT_NOT_SUPPORTED) {
            ResetPasswordFailure.NotSupported
        } else {
            ResetPasswordFailure.Inline(ResetPasswordError.UNEXPECTED)
        }
    is DomainError.Unauthenticated,
    is DomainError.NotFound,
    is DomainError.Conflict,
    is DomainError.Unknown,
    -> ResetPasswordFailure.Inline(ResetPasswordError.UNEXPECTED)
}

// Validación local, antes de la red: solo depende de lo escrito, nunca del servidor.
fun resetPasswordValidationErrorFor(error: DomainError.Validation): ResetPasswordError = when (error.code) {
    AuthRules.EMAIL_INVALID -> ResetPasswordError.EMAIL_INVALID
    AuthRules.CODE_INVALID -> ResetPasswordError.CODE_INVALID
    AuthRules.PASSWORD_MISMATCH -> ResetPasswordError.PASSWORD_MISMATCH
    else -> ResetPasswordError.PASSWORD_INVALID
}
