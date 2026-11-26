package com.simposio.taskeer.data.remote.session

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.ApiErrorMapper
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.HttpTransport
import com.simposio.taskeer.data.remote.TransportResult
import com.simposio.taskeer.domain.model.DomainError
import org.json.JSONException
import org.json.JSONObject

// Contrato B8 provisional: ruta, cabecera y claves JSON se confirmarán con el backend.
// Es el único sitio que conoce la forma del refresh.
class HttpSessionRefresher(
    private val transport: HttpTransport,
    private val config: ApiConfig,
) : SessionRefresher {

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = config.baseUrl + REFRESH_PATH,
            headers = mapOf(
                "Accept" to "application/json",
                "X-Client" to "mobile",
                "User-Agent" to config.userAgent,
                "Content-Type" to "application/json; charset=utf-8",
            ),
            body = JSONObject().put("refreshToken", refreshToken).toString(),
        )
        return when (val result = transport.send(request)) {
            is TransportResult.Failure ->
                RefreshOutcome.Unavailable(ApiErrorMapper.networkError(result.kind))
            is TransportResult.Response -> classify(result)
        }
    }

    private fun classify(response: TransportResult.Response): RefreshOutcome {
        if (response.status in 200..299) {
            return parseTokens(response.body)
                ?: RefreshOutcome.Unavailable(
                    DomainError.Server("INVALID_REFRESH_RESPONSE", "Respuesta de sesión no válida del servidor"),
                )
        }
        val failure = ApiErrorMapper.map(response.status, response.headers, response.body)
        val isRejection = response.status == 401 &&
            ApiErrorMapper.errorCode(response.body) in REJECTION_CODES
        return if (isRejection) {
            RefreshOutcome.Rejected(failure.error)
        } else {
            RefreshOutcome.Unavailable(failure.error, failure.retryAfterSeconds)
        }
    }

    private fun parseTokens(body: String): RefreshOutcome.Renewed? = try {
        val json = JSONObject(body)
        val access = json.optString("accessToken")
        val refresh = json.optString("refreshToken")
        if (access.isBlank() || refresh.isBlank()) null else RefreshOutcome.Renewed(access, refresh)
    } catch (_: JSONException) {
        null
    }

    private companion object {
        const val REFRESH_PATH = "auth/refresh"

        // REFRESH_TOKEN_REUSED y REFRESH_TOKEN_REVOKED son provisionales hasta confirmarlos con backend.
        val REJECTION_CODES = setOf(
            "UNAUTHENTICATED",
            "INVALID_REFRESH_TOKEN",
            "REFRESH_TOKEN_REUSED",
            "REFRESH_TOKEN_REVOKED",
        )
    }
}
