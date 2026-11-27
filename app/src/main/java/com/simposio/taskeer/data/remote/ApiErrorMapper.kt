package com.simposio.taskeer.data.remote

import com.simposio.taskeer.domain.model.DomainError
import org.json.JSONException
import org.json.JSONObject

object ApiErrorMapper {

    const val MOBILE_CLIENT_NOT_SUPPORTED = "MOBILE_CLIENT_NOT_SUPPORTED"
    private const val UNTRUSTED_ORIGIN = "UNTRUSTED_ORIGIN"
    const val MOBILE_CLIENT_MESSAGE =
        "El servidor no admite todavía esta versión de la app. Actualiza la aplicación o inténtalo más tarde."

    fun map(status: Int, headers: Map<String, String>, body: String): ApiResult.Failure =
        ApiResult.Failure(mapError(status, body), retryAfterSeconds(headers), status)

    fun mapError(status: Int, body: String): DomainError {
        val envelope = parseEnvelope(body)
        if (envelope != null && status == 403 && envelope.code == UNTRUSTED_ORIGIN) {
            return DomainError.Forbidden(MOBILE_CLIENT_NOT_SUPPORTED, MOBILE_CLIENT_MESSAGE)
        }
        if (envelope != null) {
            val mapped = DomainError.fromApi(envelope.code, envelope.message)
            if (mapped !is DomainError.Unknown) return mapped
            return classifyByStatus(status, envelope.code, envelope.message)
        }
        return classifyByStatus(status, code = null, message = null)
    }

    fun errorCode(body: String): String? = parseEnvelope(body)?.code

    private class Envelope(val code: String, val message: String)

    private fun parseEnvelope(body: String): Envelope? {
        if (body.isBlank()) return null
        return try {
            val error = JSONObject(body).getJSONObject("error")
            val code = error.optString("code")
            if (code.isEmpty()) null else Envelope(code, error.optString("message"))
        } catch (_: JSONException) {
            null
        }
    }

    private fun classifyByStatus(status: Int, code: String?, message: String?): DomainError {
        val text = message?.takeIf { it.isNotBlank() }
        return when (status) {
            400, 422 -> DomainError.Validation(code ?: "VALIDATION_ERROR", text ?: "Los datos enviados no son válidos")
            401 -> DomainError.Unauthenticated(code ?: "UNAUTHENTICATED", text ?: "Tu sesión no es válida")
            403 -> DomainError.Forbidden(code ?: "FORBIDDEN", text ?: "No tienes permiso para esta acción")
            404 -> DomainError.NotFound(code ?: "NOT_FOUND", text ?: "No se encontró el recurso")
            409 -> DomainError.Conflict(code ?: "CONFLICT", text ?: "La operación entra en conflicto con el estado actual")
            429 -> DomainError.RateLimited(
                code ?: "TOO_MANY_REQUESTS",
                text ?: "Demasiadas peticiones. Inténtalo en unos minutos",
            )
            in 500..599 -> DomainError.Server(
                code ?: "INTERNAL_SERVER_ERROR",
                "Error del servidor. Inténtalo de nuevo más tarde",
            )
            else -> DomainError.Unknown(code ?: "HTTP_$status", text ?: "No se pudo completar la operación")
        }
    }

    private fun retryAfterSeconds(headers: Map<String, String>): Long? =
        listOf("retry-after", "ratelimit-reset").firstNotNullOfOrNull { name ->
            headers[name]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        }

    fun networkError(kind: TransportFailureKind): DomainError.Network = when (kind) {
        TransportFailureKind.UNREACHABLE ->
            DomainError.Network("NETWORK_UNREACHABLE", "No se pudo conectar con el servidor. Revisa tu conexión")
        TransportFailureKind.TIMEOUT ->
            DomainError.Network("NETWORK_TIMEOUT", "El servidor tardó demasiado en responder")
        TransportFailureKind.TLS ->
            DomainError.Network("NETWORK_TLS", "No se pudo establecer una conexión segura con el servidor")
        TransportFailureKind.IO ->
            DomainError.Network("NETWORK_IO", "Se interrumpió la comunicación con el servidor")
    }
}
