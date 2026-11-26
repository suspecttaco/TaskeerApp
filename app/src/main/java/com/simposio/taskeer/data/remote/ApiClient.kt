package com.simposio.taskeer.data.remote

import com.simposio.taskeer.domain.model.DomainError

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

enum class AuthMode { BEARER, NONE }

class ApiRequest(
    val method: HttpMethod,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val jsonBody: String? = null,
    val auth: AuthMode = AuthMode.BEARER,
) {
    override fun toString(): String = "ApiRequest($method $path)"
}

class ApiResponse(val status: Int, val body: String) {
    override fun toString(): String = "ApiResponse($status)"
}

sealed interface ApiResult {
    data class Success(val response: ApiResponse) : ApiResult

    data class Failure(val error: DomainError, val retryAfterSeconds: Long? = null) : ApiResult
}

interface ApiClient {
    suspend fun execute(request: ApiRequest): ApiResult
}
