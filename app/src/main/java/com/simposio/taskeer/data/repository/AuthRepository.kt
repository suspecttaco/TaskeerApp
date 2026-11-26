package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleSignInOutcome
import com.simposio.taskeer.domain.auth.LoginOutcome
import com.simposio.taskeer.domain.auth.LogoutOutcome
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.auth.ResendVerificationOutcome
import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.auth.VerifyEmailOutcome
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun resume()

    // Rellena `password` de ceros en todos los caminos: el llamante no debe reutilizarla.
    suspend fun login(email: String, password: CharArray): LoginOutcome

    // Requiere que no haya sesión iniciada (si la hay, no hace petición). No conserva el credencial. Un intento, sin reintentos.
    suspend fun signInWithGoogle(credential: GoogleCredential): GoogleSignInOutcome

    // Rellena `password` de ceros en todos los caminos. No inicia sesión ni toca `sessionState`.
    suspend fun register(displayName: String, email: String, password: CharArray): RegisterOutcome

    // Público: no exige sesión ni la toca. Acepta el token o el enlace completo; no se conserva.
    suspend fun verifyEmail(token: String): VerifyEmailOutcome

    // Requiere `SignedIn`; sin sesión no hace petición.
    suspend fun resendVerification(): ResendVerificationOutcome

    // Público: no toca la sesión. Rellena `newPassword` de ceros en todos los caminos. Un código por llamada, sin reintentos.
    suspend fun resetPassword(email: String, code: String, newPassword: CharArray): ResetPasswordOutcome

    // Requiere `SignedIn` (sin sesión no hay petición). Rellena ambas contraseñas de ceros. Con éxito sustituye la sesión.
    suspend fun changePassword(currentPassword: CharArray, newPassword: CharArray): ChangePasswordOutcome

    // Requiere `SignedIn` (sin sesión no hay petición). Rellena `currentPassword` de ceros.
    suspend fun regenerateRecoveryCodes(currentPassword: CharArray): RegenerateCodesOutcome

    suspend fun logout(): LogoutOutcome

    fun acknowledgeExpiration()
}
