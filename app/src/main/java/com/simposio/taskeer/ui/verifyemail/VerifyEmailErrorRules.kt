package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.VerificationInput
import com.simposio.taskeer.domain.model.DomainError

enum class VerifyEmailOperation { VERIFY, RESEND }

private const val EMAIL_VERIFICATION_DISABLED = "EMAIL_VERIFICATION_DISABLED"

// Nunca se muestra `error.message` del servidor: el texto lo decide el cliente según el tipo de error.
fun verifyEmailErrorFor(error: DomainError, operation: VerifyEmailOperation): VerifyEmailError = when (error) {
    is DomainError.Validation -> when {
        error.code == VerificationInput.TOKEN_INVALID -> VerifyEmailError.INPUT_INVALID
        operation == VerifyEmailOperation.VERIFY -> VerifyEmailError.INVALID_OR_EXPIRED
        else -> VerifyEmailError.UNEXPECTED
    }
    is DomainError.Unauthenticated ->
        if (operation == VerifyEmailOperation.RESEND) VerifyEmailError.NEEDS_SIGN_IN else VerifyEmailError.UNEXPECTED
    is DomainError.RateLimited -> VerifyEmailError.RATE_LIMITED
    is DomainError.Network -> VerifyEmailError.NETWORK
    // La suspensión del envío es un estado del backend, no un fallo de la app.
    is DomainError.Server ->
        if (error.code == EMAIL_VERIFICATION_DISABLED) VerifyEmailError.DISABLED else VerifyEmailError.SERVER
    is DomainError.Forbidden ->
        if (error.code == AuthRules.MOBILE_CLIENT_NOT_SUPPORTED && operation == VerifyEmailOperation.RESEND) {
            VerifyEmailError.NOT_SUPPORTED
        } else {
            VerifyEmailError.UNEXPECTED
        }
    is DomainError.NotFound,
    is DomainError.Conflict,
    is DomainError.Unknown,
    -> VerifyEmailError.UNEXPECTED
}
