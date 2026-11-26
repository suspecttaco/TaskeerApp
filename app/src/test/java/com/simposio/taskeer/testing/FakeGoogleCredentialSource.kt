package com.simposio.taskeer.testing

import com.simposio.taskeer.data.auth.google.GoogleCredentialSource
import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import kotlinx.coroutines.CompletableDeferred

class FakeGoogleCredentialSource : GoogleCredentialSource {
    var result: GoogleCredentialResult = GoogleCredentialResult.Obtained(GoogleCredential("id-token-secreto", "nonce-secreto"))
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    val hosts = mutableListOf<Any>()

    override suspend fun obtain(host: Any): GoogleCredentialResult {
        calls++
        hosts += host
        gate?.await()
        return result
    }
}
