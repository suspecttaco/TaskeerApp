package com.simposio.taskeer.domain.auth

// No es `data class`: el `toString()` derivado mostraría el token. `rawNonce` va en claro al backend.
class GoogleCredential(val idToken: String, val rawNonce: String) {
    override fun toString(): String = "GoogleCredential(redacted)"
}

sealed interface GoogleCredentialResult {
    class Obtained(val credential: GoogleCredential) : GoogleCredentialResult {
        override fun toString(): String = "Obtained(redacted)"
    }

    // El usuario cerró el selector: no es un error y no lleva mensaje.
    data object Cancelled : GoogleCredentialResult

    data object NoAccount : GoogleCredentialResult

    // Sin Play Services, sin Web client ID o con un `host` que no es una Activity.
    data object Unavailable : GoogleCredentialResult

    data object Failed : GoogleCredentialResult
}

data class GoogleSignInConfig(val webClientId: String) {
    val isAvailable: Boolean get() = webClientId.isNotBlank()
}
