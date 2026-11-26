package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError

sealed interface LoginOutcome {
    data class SignedIn(val account: AccountSummary) : LoginOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : LoginOutcome
}

data class LogoutOutcome(val serverRevoked: Boolean)
