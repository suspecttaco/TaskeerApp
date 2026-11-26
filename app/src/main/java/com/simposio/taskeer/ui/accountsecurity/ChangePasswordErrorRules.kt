package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError

private const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
private const val NO_LOCAL_PASSWORD = "NO_LOCAL_PASSWORD"
private const val CONCURRENT_PASSWORD_CHANGE = "CONCURRENT_PASSWORD_CHANGE"

// Se decide por `error.code` antes que por el tipo: un 401 de contraseña actual no es una sesión caducada.
// Nunca se muestra `error.message` del servidor.
fun changePasswordErrorFor(error: DomainError): ChangePasswordError = when (error) {
    is DomainError.Unauthenticated ->
        if (error.code == INVALID_CREDENTIALS) ChangePasswordError.CURRENT_INCORRECT else ChangePasswordError.NEEDS_SIGN_IN
    is DomainError.Validation -> when (error.code) {
        NO_LOCAL_PASSWORD -> ChangePasswordError.NO_LOCAL_PASSWORD
        AuthRules.PASSWORD_REQUIRED, AuthRules.PASSWORD_TOO_LONG -> ChangePasswordError.CURRENT_INVALID
        AuthRules.PASSWORD_TOO_SHORT -> ChangePasswordError.NEW_INVALID
        AuthRules.PASSWORD_MISMATCH -> ChangePasswordError.MISMATCH
        else -> ChangePasswordError.CHECK_DATA
    }
    is DomainError.Conflict ->
        if (error.code == CONCURRENT_PASSWORD_CHANGE) ChangePasswordError.CONCURRENT_CHANGE else ChangePasswordError.UNEXPECTED
    is DomainError.RateLimited -> ChangePasswordError.RATE_LIMITED
    is DomainError.Network -> ChangePasswordError.NETWORK
    is DomainError.Server -> ChangePasswordError.SERVER
    is DomainError.Forbidden,
    is DomainError.NotFound,
    is DomainError.Unknown,
    -> ChangePasswordError.UNEXPECTED
}
