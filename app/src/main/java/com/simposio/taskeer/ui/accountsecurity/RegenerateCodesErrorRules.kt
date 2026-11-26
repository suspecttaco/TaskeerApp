package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

private const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
private const val NO_LOCAL_PASSWORD = "NO_LOCAL_PASSWORD"

// Se decide por `error.code` antes que por el tipo. Nunca se muestra `error.message` del servidor.
fun regenerateCodesErrorFor(error: DomainError): RegenerateCodesError = when (error) {
    is DomainError.Unauthenticated ->
        if (error.code == INVALID_CREDENTIALS) RegenerateCodesError.CURRENT_INCORRECT else RegenerateCodesError.NEEDS_SIGN_IN
    is DomainError.Validation -> when (error.code) {
        NO_LOCAL_PASSWORD -> RegenerateCodesError.NO_LOCAL_PASSWORD
        AuthRules.PASSWORD_REQUIRED, AuthRules.PASSWORD_TOO_LONG -> RegenerateCodesError.CURRENT_INVALID
        else -> RegenerateCodesError.CHECK_DATA
    }
    is DomainError.RateLimited -> RegenerateCodesError.RATE_LIMITED
    is DomainError.Network -> RegenerateCodesError.NETWORK
    is DomainError.Server -> RegenerateCodesError.SERVER
    is DomainError.Forbidden,
    is DomainError.NotFound,
    is DomainError.Conflict,
    is DomainError.Unknown,
    -> RegenerateCodesError.UNEXPECTED
}
