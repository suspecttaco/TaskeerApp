package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError

sealed interface LoginOutcome {
    data class SignedIn(val account: AccountSummary) : LoginOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : LoginOutcome
}

sealed interface RegisterOutcome {
    data class Registered(val account: AccountSummary, val recoveryCodes: RecoveryCodes) : RegisterOutcome

    // La cuenta existe en el servidor aunque no llegaran códigos: reintentar daría 409.
    data class RegisteredWithoutCodes(val email: String) : RegisterOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : RegisterOutcome
}

data class LogoutOutcome(val serverRevoked: Boolean)
