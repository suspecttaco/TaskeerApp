package com.simposio.taskeer.data.remote

interface HttpTransport {
    suspend fun send(request: HttpRequest): TransportResult
}

class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
) {
    override fun toString(): String = "HttpRequest($method)"
}

sealed interface TransportResult {
    class Response(
        val status: Int,
        val headers: Map<String, String>,
        val body: String,
    ) : TransportResult

    class Failure(val kind: TransportFailureKind) : TransportResult
}

enum class TransportFailureKind { UNREACHABLE, TIMEOUT, TLS, IO }
