package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.model.DomainError

// Nunca se muestra `error.message` del servidor: el texto lo decide el cliente según el código y el tipo de error.
fun googleFailureFor(error: DomainError): LoginFailure = when (error) {
    is DomainError.Unauthenticated -> LoginFailure.Inline(
        if (error.code == AuthRules.INVALID_GOOGLE_TOKEN) LoginError.GOOGLE_TOKEN_INVALID else LoginError.GOOGLE_UNEXPECTED,
    )
    is DomainError.Forbidden -> when (error.code) {
        AuthRules.MOBILE_CLIENT_NOT_SUPPORTED -> LoginFailure.NotSupported
        AuthRules.GOOGLE_EMAIL_NOT_VERIFIED -> LoginFailure.Inline(LoginError.GOOGLE_EMAIL_NOT_VERIFIED)
        else -> LoginFailure.Inline(LoginError.GOOGLE_UNEXPECTED)
    }
    is DomainError.Conflict -> LoginFailure.Inline(
        if (error.code == AuthRules.LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED) {
            LoginError.LOCAL_ACCOUNT_NOT_VERIFIED
        } else {
            LoginError.GOOGLE_UNEXPECTED
        },
    )
    is DomainError.Validation -> LoginFailure.Inline(
        if (error.code == AuthRules.ALREADY_SIGNED_IN) LoginError.GOOGLE_UNEXPECTED else LoginError.GOOGLE_REJECTED,
    )
    is DomainError.RateLimited -> LoginFailure.Inline(LoginError.RATE_LIMITED)
    is DomainError.Network -> LoginFailure.Inline(LoginError.NETWORK)
    is DomainError.Server -> LoginFailure.Inline(LoginError.SERVER)
    is DomainError.NotFound,
    is DomainError.Unknown,
    -> LoginFailure.Inline(LoginError.GOOGLE_UNEXPECTED)
}

// null: no hay error que mostrar (credencial obtenida o selector cerrado por el usuario).
fun googleResultErrorFor(result: GoogleCredentialResult): LoginError? = when (result) {
    is GoogleCredentialResult.Obtained, GoogleCredentialResult.Cancelled -> null
    GoogleCredentialResult.NoAccount -> LoginError.GOOGLE_NO_ACCOUNT
    GoogleCredentialResult.Unavailable -> LoginError.GOOGLE_UNAVAILABLE
    GoogleCredentialResult.Failed -> LoginError.GOOGLE_FAILED
}
