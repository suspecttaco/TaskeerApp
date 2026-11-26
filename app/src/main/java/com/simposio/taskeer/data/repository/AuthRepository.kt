package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.SessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun resume()

    // Rellena `password` de ceros en todos los caminos: el llamante no debe reutilizarla.
    suspend fun login(email: String, password: CharArray): LoginOutcome

    suspend fun logout(): LogoutOutcome

    fun acknowledgeExpiration()
}
