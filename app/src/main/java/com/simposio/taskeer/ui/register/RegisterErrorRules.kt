package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface RegisterFailure {
    data class Inline(val error: RegisterError) : RegisterFailure

    data object NotSupported : RegisterFailure
}

private const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED"

// Nunca se muestra `error.message` del servidor: el texto lo decide el cliente según el tipo de error.
fun registerFailureFor(error: DomainError): RegisterFailure = when (error) {
    is DomainError.Conflict ->
        RegisterFailure.Inline(
            if (error.code == EMAIL_ALREADY_REGISTERED) RegisterError.EMAIL_TAKEN else RegisterError.UNEXPECTED,
        )
    is DomainError.RateLimited -> RegisterFailure.Inline(RegisterError.RATE_LIMITED)
    is DomainError.Network -> RegisterFailure.Inline(RegisterError.NETWORK)
    is DomainError.Server -> RegisterFailure.Inline(RegisterError.SERVER)
    is DomainError.Forbidden ->
        if (error.code == AuthRules.MOBILE_CLIENT_NOT_SUPPORTED) {
            RegisterFailure.NotSupported
        } else {
            RegisterFailure.Inline(RegisterError.UNEXPECTED)
        }
    is DomainError.Validation -> RegisterFailure.Inline(
        when (error.code) {
            AuthRules.DISPLAY_NAME_INVALID -> RegisterError.NAME_INVALID
            AuthRules.EMAIL_INVALID -> RegisterError.EMAIL_INVALID
            AuthRules.PASSWORD_TOO_SHORT -> RegisterError.PASSWORD_TOO_SHORT
            AuthRules.PASSWORD_TOO_LONG -> RegisterError.PASSWORD_TOO_LONG
            AuthRules.PASSWORD_MISMATCH -> RegisterError.PASSWORD_MISMATCH
            AuthRules.ALREADY_SIGNED_IN -> RegisterError.UNEXPECTED
            else -> RegisterError.VALIDATION
        },
    )
    is DomainError.Unauthenticated,
    is DomainError.NotFound,
    is DomainError.Unknown,
    -> RegisterFailure.Inline(RegisterError.UNEXPECTED)
}
