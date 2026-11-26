package com.simposio.taskeer.data.remote

import com.simposio.taskeer.data.remote.session.AuthSession
import com.simposio.taskeer.data.remote.session.RenewOutcome
import com.simposio.taskeer.domain.model.DomainError
import java.net.URLEncoder

class RealApiClient(
    private val transport: HttpTransport,
    private val session: AuthSession,
    private val config: ApiConfig,
) : ApiClient {

    override suspend fun execute(request: ApiRequest): ApiResult {
        if (isAbsolute(request.path)) {
            return ApiResult.Failure(
                DomainError.Validation("INVALID_REQUEST_PATH", "La ruta de la petición no es válida"),
            )
        }
        val first = attempt(request, bearerToken = session.currentAccessToken())
        if (request.auth != AuthMode.BEARER || !first.needsRenewal) return first.result

        return when (val renewed = session.renew(first.usedToken)) {
            is RenewOutcome.Renewed -> attempt(request, renewed.accessToken).result
            RenewOutcome.SessionExpired -> ApiResult.Failure(
                DomainError.Unauthenticated("UNAUTHENTICATED", "Tu sesión ha caducado. Inicia sesión de nuevo"),
            )
            is RenewOutcome.Failed -> ApiResult.Failure(renewed.error, renewed.retryAfterSeconds)
        }
    }

    private class Attempt(val result: ApiResult, val usedToken: String?, val needsRenewal: Boolean)

    private suspend fun attempt(request: ApiRequest, bearerToken: String?): Attempt {
        val token = if (request.auth == AuthMode.BEARER) bearerToken else null
        val response = transport.send(buildHttpRequest(request, token))
        return when (response) {
            is TransportResult.Failure -> Attempt(
                ApiResult.Failure(ApiErrorMapper.networkError(response.kind)),
                token,
                needsRenewal = false,
            )
            is TransportResult.Response -> {
                if (response.status in 200..299) {
                    Attempt(ApiResult.Success(ApiResponse(response.status, response.body)), token, false)
                } else {
                    val rejected = response.status == 401 &&
                        ApiErrorMapper.errorCode(response.body) == "UNAUTHENTICATED"
                    Attempt(ApiErrorMapper.map(response.status, response.headers, response.body), token, rejected)
                }
            }
        }
    }

    private fun buildHttpRequest(request: ApiRequest, token: String?): HttpRequest {
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "X-Client" to "mobile",
            "User-Agent" to config.userAgent,
        )
        if (request.jsonBody != null) headers["Content-Type"] = "application/json; charset=utf-8"
        if (token != null) headers["Authorization"] = "Bearer $token"
        return HttpRequest(request.method, buildUrl(request), headers, request.jsonBody)
    }

    private fun buildUrl(request: ApiRequest): String {
        val base = config.baseUrl + request.path.trimStart('/')
        if (request.query.isEmpty()) return base
        val query = request.query.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        return "$base?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun isAbsolute(path: String): Boolean =
        path.startsWith("//") || SCHEME.containsMatchIn(path)

    private companion object {
        val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }
}
