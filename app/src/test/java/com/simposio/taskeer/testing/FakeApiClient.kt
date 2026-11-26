package com.simposio.taskeer.testing

import com.simposio.taskeer.data.remote.ApiClient
import com.simposio.taskeer.data.remote.ApiRequest
import com.simposio.taskeer.data.remote.ApiResponse
import com.simposio.taskeer.data.remote.ApiResult

class FakeApiClient : ApiClient {
    val requests = mutableListOf<ApiRequest>()
    private val queue = ArrayDeque<suspend (ApiRequest) -> ApiResult>()
    var fallback: (suspend (ApiRequest) -> ApiResult)? = null

    fun enqueue(result: ApiResult) {
        queue.addLast { result }
    }

    fun enqueue(handler: suspend (ApiRequest) -> ApiResult) {
        queue.addLast(handler)
    }

    override suspend fun execute(request: ApiRequest): ApiResult {
        requests += request
        val handler = queue.removeFirstOrNull() ?: fallback ?: error("FakeApiClient sin respuesta para $request")
        return handler(request)
    }
}

fun apiOk(body: String = "{}", status: Int = 200): ApiResult = ApiResult.Success(ApiResponse(status, body))
