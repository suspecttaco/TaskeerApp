package com.simposio.taskeer.data.auth.google

import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.auth.GoogleSignInConfig

interface GoogleCredentialSource {
    // `host` debe ser la Activity que muestra la hoja del sistema; no se guarda.
    suspend fun obtain(host: Any): GoogleCredentialResult
}

object UnavailableGoogleCredentialSource : GoogleCredentialSource {
    override suspend fun obtain(host: Any): GoogleCredentialResult = GoogleCredentialResult.Unavailable
}

// Único puente hacia Credential Manager: di/ compone esta función sin importar `androidx.credentials`.
fun createGoogleCredentialSource(config: GoogleSignInConfig): GoogleCredentialSource =
    if (config.isAvailable) CredentialManagerGoogleSource(config) else UnavailableGoogleCredentialSource
