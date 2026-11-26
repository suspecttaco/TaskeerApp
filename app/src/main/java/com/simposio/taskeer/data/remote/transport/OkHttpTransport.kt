package com.simposio.taskeer.data.remote.transport

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.HttpMethod
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.HttpTransport
import com.simposio.taskeer.data.remote.TransportFailureKind
import com.simposio.taskeer.data.remote.TransportResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OkHttpTransport(private val config: ApiConfig) : HttpTransport {

    private val client: OkHttpClient by lazy { buildClient(config) }

    override suspend fun send(request: HttpRequest): TransportResult {
        val call = try {
            client.newCall(buildRequest(request))
        } catch (_: IllegalArgumentException) {
            return TransportResult.Failure(TransportFailureKind.IO)
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) return
                    continuation.resume(TransportResult.Failure(classify(e)))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { toResult(it) }
                    } catch (e: IOException) {
                        if (call.isCanceled()) return
                        TransportResult.Failure(classify(e))
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    private fun buildRequest(request: HttpRequest): Request {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val requiresBody = request.method in setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
        val body: RequestBody? = when {
            request.body != null -> request.body.toRequestBody(JSON_MEDIA_TYPE)
            requiresBody -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        return builder.method(request.method.name, body).build()
    }

    private fun toResult(response: Response): TransportResult {
        val headers = response.headers.names().associate { name ->
            name.lowercase() to response.headers.values(name).joinToString(", ")
        }
        return TransportResult.Response(response.code, headers, response.body.string())
    }

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildClient(config: ApiConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(config.callTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            // La API no redirige: una 3xx es un fallo de configuración y no debe reenviar la petición en silencio.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        fun classify(error: IOException): TransportFailureKind = when (error) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException ->
                TransportFailureKind.UNREACHABLE
            is InterruptedIOException -> TransportFailureKind.TIMEOUT
            is SSLException -> TransportFailureKind.TLS
            else -> TransportFailureKind.IO
        }
    }
}
