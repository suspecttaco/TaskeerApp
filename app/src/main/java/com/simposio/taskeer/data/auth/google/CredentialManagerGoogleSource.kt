package com.simposio.taskeer.data.auth.google

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.auth.GoogleSignInConfig
import kotlinx.coroutines.CancellationException

internal sealed interface CredentialClientResult {
    class IdToken(val value: String) : CredentialClientResult {
        override fun toString(): String = "IdToken(redacted)"
    }

    data object UserCancelled : CredentialClientResult

    data object NoCredential : CredentialClientResult

    data object ProviderMissing : CredentialClientResult

    data object Other : CredentialClientResult
}

// Costura de la única llamada a Credential Manager, que necesita una Activity y Play Services y no corre en JVM.
internal interface CredentialClient {
    suspend fun request(host: Any, webClientId: String, hashedNonce: String): CredentialClientResult
}

class CredentialManagerGoogleSource internal constructor(
    private val config: GoogleSignInConfig,
    private val client: CredentialClient,
    private val isHost: (Any) -> Boolean,
) : GoogleCredentialSource {

    constructor(config: GoogleSignInConfig) : this(config, RealCredentialClient(), { it is Activity })

    override suspend fun obtain(host: Any): GoogleCredentialResult {
        if (!config.isAvailable || !isHost(host)) return GoogleCredentialResult.Unavailable
        val nonce = GoogleNonce.generate()
        return try {
            when (val result = client.request(host, config.webClientId, nonce.sha256Hex)) {
                is CredentialClientResult.IdToken ->
                    GoogleCredentialResult.Obtained(GoogleCredential(result.value, nonce.raw))
                CredentialClientResult.UserCancelled -> GoogleCredentialResult.Cancelled
                CredentialClientResult.NoCredential -> GoogleCredentialResult.NoAccount
                CredentialClientResult.ProviderMissing -> GoogleCredentialResult.Unavailable
                CredentialClientResult.Other -> GoogleCredentialResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GoogleCredentialResult.Failed
        }
    }
}

internal class RealCredentialClient : CredentialClient {
    override suspend fun request(host: Any, webClientId: String, hashedNonce: String): CredentialClientResult {
        val activity = host as? Activity ?: return CredentialClientResult.ProviderMissing
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                CredentialClientResult.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                CredentialClientResult.Other
            }
        } catch (e: GetCredentialException) {
            credentialFailureOf(e)
        } catch (e: GoogleIdTokenParsingException) {
            credentialFailureOf(e)
        }
    }
}

// Pura para poder probar cada tipo de excepción en JVM, sin Activity ni Play Services.
internal fun credentialFailureOf(error: Throwable): CredentialClientResult = when (error) {
    is GetCredentialCancellationException -> CredentialClientResult.UserCancelled
    // Recreación de la Activity o muerte del proceso del selector: vuelve al reposo sin error.
    is GetCredentialInterruptedException -> CredentialClientResult.UserCancelled
    is NoCredentialException -> CredentialClientResult.NoCredential
    is GetCredentialProviderConfigurationException -> CredentialClientResult.ProviderMissing
    else -> CredentialClientResult.Other
}
