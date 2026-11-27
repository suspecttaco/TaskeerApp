package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.model.DomainError

fun interface GoogleTokenExchange {
    suspend fun exchange(credential: GoogleCredential): GoogleExchangeResult
}

sealed interface GoogleExchangeResult {
    // No es `data class`: el `toString()` derivado mostraría el token de Supabase.
    class Token(val accessToken: String) : GoogleExchangeResult {
        override fun toString(): String = "Token(redacted)"
    }

    class Failed(val error: DomainError, val retryAfterSeconds: Long? = null) : GoogleExchangeResult {
        override fun toString(): String = "Failed(${error.code})"
    }
}
