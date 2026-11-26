package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

sealed interface LoginFailure {
    data class Inline(val error: LoginError) : LoginFailure

    data object NotSupported : LoginFailure
}

// Nunca se muestra `error.message` del servidor: el texto lo decide el cliente según el tipo de error.
fun loginFailureFor(error: DomainError): LoginFailure = when (error) {
    is DomainError.Unauthenticated -> LoginFailure.Inline(LoginError.INVALID_CREDENTIALS)
    is DomainError.RateLimited -> LoginFailure.Inline(LoginError.RATE_LIMITED)
    is DomainError.Network -> LoginFailure.Inline(LoginError.NETWORK)
    is DomainError.Server -> LoginFailure.Inline(LoginError.SERVER)
    is DomainError.Forbidden ->
        if (error.code == AuthRules.MOBILE_CLIENT_NOT_SUPPORTED) {
            LoginFailure.NotSupported
        } else {
            LoginFailure.Inline(LoginError.UNEXPECTED)
        }
    is DomainError.Validation -> LoginFailure.Inline(
        when (error.code) {
            AuthRules.EMAIL_INVALID -> LoginError.EMAIL_INVALID
            AuthRules.PASSWORD_REQUIRED -> LoginError.PASSWORD_REQUIRED
            AuthRules.PASSWORD_TOO_LONG -> LoginError.PASSWORD_TOO_LONG
            AuthRules.ALREADY_SIGNED_IN -> LoginError.UNEXPECTED
            else -> LoginError.VALIDATION
        },
    )
    is DomainError.NotFound,
    is DomainError.Conflict,
    is DomainError.Unknown,
    -> LoginFailure.Inline(LoginError.UNEXPECTED)
}
