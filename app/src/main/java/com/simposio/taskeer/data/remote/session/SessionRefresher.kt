package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.domain.model.DomainError

interface SessionRefresher {
    suspend fun refresh(refreshToken: String): RefreshOutcome
}

sealed interface RefreshOutcome {
    data class Renewed(val accessToken: String, val refreshToken: String) : RefreshOutcome

    data class Rejected(val error: DomainError) : RefreshOutcome

    data class Unavailable(
        val error: DomainError,
        val retryAfterSeconds: Long? = null,
    ) : RefreshOutcome
}

sealed interface RenewOutcome {
    data class Renewed(val accessToken: String) : RenewOutcome

    data object SessionExpired : RenewOutcome

    data class Failed(val error: DomainError, val retryAfterSeconds: Long?) : RenewOutcome
}
