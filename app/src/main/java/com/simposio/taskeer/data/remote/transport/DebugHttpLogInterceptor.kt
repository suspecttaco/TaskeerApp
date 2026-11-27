package com.simposio.taskeer.data.remote.transport

import android.util.Log
import java.io.IOException
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

internal class DebugHttpLogInterceptor(
    private val sink: (String) -> Unit = { Log.d(TAG, it) },
    private val nanoTime: () -> Long = System::nanoTime,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = safeUrl(request.url)
        val hideBodies = carriesTaskContent(request.url.encodedPath)
        sink("--> ${request.method} $url")
        logHeaders(request.headers, null)
        if (hideBodies) request.body?.let { sink("<omitido: contenido de tareas>") } else logRequestBody(request)
        val start = nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            sink("<-- FAILED $url: ${e.javaClass.simpleName}: ${e.message} (${elapsedMs(start)} ms)")
            throw e
        }
        sink("<-- ${response.code} $url (${elapsedMs(start)} ms)")
        logHeaders(response.headers, RESPONSE_HEADERS)
        if (hideBodies) {
            sink("<cuerpo omitido: contenido de tareas>")
        } else if (!isEventStream(response)) {
            val body = runCatching { response.peekBody(PEEK_BYTES).string() }.getOrDefault("")
            if (body.isNotEmpty()) sink(mask(body).take(MAX_BODY_CHARS))
        }
        return response
    }

    private fun logHeaders(headers: Headers, allowed: Set<String>?) {
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val lower = name.lowercase()
            when {
                lower in MASKED_HEADERS -> sink("$name: $MASK")
                allowed == null || lower in allowed -> sink("$name: ${headers.value(i)}")
            }
        }
    }

    private fun logRequestBody(request: Request) {
        val body = request.body ?: return
        val length = body.contentLength()
        val json = body.contentType()?.subtype?.contains("json", ignoreCase = true) == true
        if (!json || body.isOneShot() || body.isDuplex() || length !in 0..MAX_REQUEST_BYTES) {
            sink("<${if (length >= 0) length else "?"} bytes>")
            return
        }
        val text = runCatching { Buffer().also(body::writeTo).readUtf8() }.getOrNull()
        sink(if (text != null) mask(text).take(MAX_BODY_CHARS) else "<$length bytes>")
    }

    // Un stream SSE no termina: peekBody se bloquearía esperando bytes.
    private fun isEventStream(response: Response) =
        response.header("Content-Type")?.contains("text/event-stream", ignoreCase = true) == true

    // La URL real lleva el prefijo /api/v1/, así que «sync» no es el primer segmento: se busca en cualquier posición.
    // Sincronización, listas, tareas y subtareas llevan títulos y descripciones del usuario.
    private fun carriesTaskContent(path: String): Boolean {
        val segments = path.trim('/').split('/')
        return segments.any { it in CONTENT_SEGMENTS }
    }

    private fun elapsedMs(start: Long) = (nanoTime() - start) / 1_000_000

    // La query puede llevar tokens de verificación o recuperación.
    private fun safeUrl(url: okhttp3.HttpUrl) = url.newBuilder().query(null).fragment(null).build().toString()

    internal companion object {
        const val TAG = "TaskeerHttp"
        private const val MAX_BODY_CHARS = 2000
        private const val PEEK_BYTES = 8192L
        private const val MAX_REQUEST_BYTES = 65_536L
        private val CONTENT_SEGMENTS = setOf("sync", "lists", "tasks", "subtasks")
        private const val MASK = "***"
        private val MASKED_HEADERS = setOf("authorization", "proxy-authorization", "cookie", "set-cookie")
        private val RESPONSE_HEADERS = setOf("content-type", "retry-after")
        private val SECRET_KEY = "[^\"]*(?:token|password|secret|code|otp|nonce|authorization|cookie)[^\"]*"
        private val STRING_FIELD = Regex("\"($SECRET_KEY)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"", RegexOption.IGNORE_CASE)
        private val ARRAY_FIELD = Regex("\"($SECRET_KEY)\"\\s*:\\s*\\[[^\\]]*]", RegexOption.IGNORE_CASE)
        private val ERROR_CODE = Regex("[A-Z_]+")

        // Longitud y bordes bastan para detectar una contraseña mal enviada (espacios, vacía) sin revelarla.
        private fun passwordShape(raw: String): String {
            val value = raw.replace("\\\"", "\"").replace("\\\\", "\\")
            return "$MASK(len=${value.length}, trimmed=${value == value.trim()})"
        }

        fun mask(body: String): String {
            val strings = STRING_FIELD.replace(body) { match ->
                val key = match.groupValues[1]
                val value = match.value.substringAfter(':').trim().trim('"')
                // Un «code» de error de negocio (INVALID_CREDENTIALS) no es secreto y es lo que se quiere ver.
                if (key.equals("code", ignoreCase = true) && ERROR_CODE.matches(value)) match.value
                else if (key.contains("password", ignoreCase = true)) "\"$key\":\"${passwordShape(value)}\""
                else "\"$key\":\"$MASK\""
            }
            return ARRAY_FIELD.replace(strings) { "\"${it.groupValues[1]}\":[\"$MASK\"]" }
        }
    }
}
