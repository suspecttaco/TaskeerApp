package com.simposio.taskeer.data.remote.transport

import com.simposio.taskeer.data.remote.ApiConfig
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.StreamResult
import com.simposio.taskeer.data.remote.StreamTransport
import com.simposio.taskeer.data.remote.TransportFailureKind
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpStreamTransport(private val config: ApiConfig) : StreamTransport {

    private val client: OkHttpClient by lazy { buildStreamClient(config) }

    override suspend fun stream(request: HttpRequest, onLine: suspend (String) -> Unit): StreamResult {
        val call = try {
            client.newCall(buildRequest(request))
        } catch (_: IllegalArgumentException) {
            return StreamResult.Failure(TransportFailureKind.IO)
        }
        return withContext(Dispatchers.IO) {
            // La lectura bloquea el hilo: al cancelar la corrutina hay que cerrar la llamada desde fuera para desbloquearla.
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val headers = response.headers.names().associate { name ->
                            name.lowercase() to response.headers.values(name).joinToString(", ")
                        }
                        return@withContext StreamResult.Rejected(response.code, headers, response.peekBody(REJECTED_BODY_LIMIT).string())
                    }
                    val source = response.body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        onLine(line)
                    }
                    StreamResult.Ended
                }
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                StreamResult.Failure(OkHttpTransport.classify(e))
            } finally {
                watcher.cancel()
            }
        }
    }

    private fun buildRequest(request: HttpRequest): Request {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.get().build()
    }

    internal companion object {
        const val REJECTED_BODY_LIMIT = 4L * 1024

        // Sin `callTimeout`: el de las peticiones normales (60 s) cortaría el flujo. El de lectura (45 s, más del doble del
        // heartbeat de 20 s) es el detector de conexión muerta.
        fun buildStreamClient(config: ApiConfig): OkHttpClient = OkHttpTransport.buildClient(config).newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
