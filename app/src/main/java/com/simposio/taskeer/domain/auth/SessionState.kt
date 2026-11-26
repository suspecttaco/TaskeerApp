package com.simposio.taskeer.domain.auth

sealed interface SessionState {
    data class SignedOut(val expired: Boolean = false) : SessionState

    data object Resuming : SessionState

    data class SignedIn(val account: AccountSummary?, val offline: Boolean) : SessionState
}

data class AccountSummary(val email: String, val displayName: String)
