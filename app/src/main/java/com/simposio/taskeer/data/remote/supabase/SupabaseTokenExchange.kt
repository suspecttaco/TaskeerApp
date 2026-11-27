package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.HttpTransport
import com.simposio.taskeer.data.remote.TransportResult
import com.simposio.taskeer.data.remote.auth.GoogleExchangeResult
import com.simposio.taskeer.data.remote.auth.GoogleTokenExchange
import com.simposio.taskeer.domain.auth.GoogleCredential

// No pasa por ApiClient: aquel añade `X-Client`, Bearer y renovación de sesión de Taskeer y asume la base del backend propio.
class SupabaseTokenExchange(
    private val transport: HttpTransport,
    private val config: ApiConfig,
    private val anonKey: String,
) : GoogleTokenExchange {

    override suspend fun exchange(credential: GoogleCredential): GoogleExchangeResult {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = "${config.baseUrl}${SupabaseAuthContract.TOKEN_PATH}?${SupabaseAuthContract.GRANT_QUERY}",
            headers = mapOf(
                "apikey" to anonKey,
                "Content-Type" to "application/json; charset=utf-8",
                "Accept" to "application/json",
                "User-Agent" to config.userAgent,
            ),
            body = SupabaseAuthContract.tokenBody(credential.idToken, credential.rawNonce),
        )
        return when (val result = transport.send(request)) {
            is TransportResult.Failure -> GoogleExchangeResult.Failed(ApiErrorMapper.networkError(result.kind))
            is TransportResult.Response -> if (result.status in 200..299) {
                SupabaseAuthContract.parseAccessToken(result.body)?.let { GoogleExchangeResult.Token(it) }
                    ?: SupabaseAuthContract.invalidResponse()
            } else {
                SupabaseAuthContract.classify(result.status, result.headers, result.body)
            }
        }
    }

    override fun toString(): String = "SupabaseTokenExchange(host=${config.baseUrl})"
}
