package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun resume()

    // Rellena `password` de ceros en todos los caminos: el llamante no debe reutilizarla.
    suspend fun login(email: String, password: CharArray): LoginOutcome

    // Rellena `password` de ceros en todos los caminos. No inicia sesión ni toca `sessionState`.
    suspend fun register(displayName: String, email: String, password: CharArray): RegisterOutcome

    // Público: no exige sesión ni la toca. Acepta el token o el enlace completo; no se conserva.
    suspend fun verifyEmail(token: String): VerifyEmailOutcome

    // Requiere `SignedIn`; sin sesión no hace petición.
    suspend fun resendVerification(): ResendVerificationOutcome

    suspend fun logout(): LogoutOutcome

    fun acknowledgeExpiration()
}
