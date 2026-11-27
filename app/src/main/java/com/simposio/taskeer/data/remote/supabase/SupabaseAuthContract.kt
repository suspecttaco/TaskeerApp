package com.simposio.taskeer.data.remote.supabase

import com.simposio.taskeer.data.remote.auth.GoogleExchangeResult
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.model.DomainError
import org.json.JSONException
import org.json.JSONObject

// Único sitio que conoce la forma de GoTrue. Nunca se lee `msg`, `message` ni `error_description`: el texto lo decide el cliente.
internal object SupabaseAuthContract {
    const val TOKEN_PATH = "auth/v1/token"
    const val GRANT_QUERY = "grant_type=id_token"
    const val REJECTED_CODE = "GOOGLE_EXCHANGE_REJECTED"
    const val UNAVAILABLE_CODE = "GOOGLE_EXCHANGE_UNAVAILABLE"
    const val INVALID_RESPONSE_CODE = "INVALID_LOGIN_RESPONSE"

    private const val PROVIDER = "google"
    private val INVALID_TOKEN_ERROR_CODES = setOf("bad_id_token", "validation_failed")
    private const val INVALID_GRANT = "invalid_grant"
    private const val UNAVAILABLE_MESSAGE = "El servicio de acceso con Google no está disponible. Inténtalo más tarde"
    private const val INVALID_RESPONSE_MESSAGE = "Respuesta de inicio de sesión no válida del servidor"
    private const val REJECTED_MESSAGE = "No se pudo completar el acceso con Google"
    private const val INVALID_TOKEN_MESSAGE = "No se pudo verificar tu cuenta de Google"
    private const val RATE_LIMITED_MESSAGE = "Demasiados intentos. Inténtalo en unos minutos"

    fun tokenBody(idToken: String, nonce: String): String =
        JSONObject().put("provider", PROVIDER).put("id_token", idToken).put("nonce", nonce).toString()

    fun parseAccessToken(body: String): String? = try {
        JSONObject(body).optString("access_token").takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
        null
    }

    // El `error_code` de GoTrue manda; `error` (OAuth) cubre las respuestas heredadas.
    fun errorCodeOf(body: String): String? = try {
        val json = JSONObject(body)
        json.optString("error_code").takeIf { it.isNotBlank() } ?: json.optString("error").takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
        null
    }

    fun invalidResponse(): GoogleExchangeResult.Failed =
        GoogleExchangeResult.Failed(DomainError.Server(INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE))

    fun classify(status: Int, headers: Map<String, String>, body: String): GoogleExchangeResult.Failed = when {
        status == 429 -> GoogleExchangeResult.Failed(
            DomainError.RateLimited("TOO_MANY_REQUESTS", RATE_LIMITED_MESSAGE),
            headers["retry-after"]?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
        )
        status in 500..599 -> GoogleExchangeResult.Failed(DomainError.Server(UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE))
        status == 400 || status == 401 || status == 403 || status == 422 -> classifyClientError(errorCodeOf(body))
        else -> GoogleExchangeResult.Failed(DomainError.Validation(REJECTED_CODE, REJECTED_MESSAGE))
    }

    private fun classifyClientError(code: String?): GoogleExchangeResult.Failed =
        if (code in INVALID_TOKEN_ERROR_CODES || code == INVALID_GRANT) {
            GoogleExchangeResult.Failed(DomainError.Unauthenticated(AuthRules.INVALID_GOOGLE_TOKEN, INVALID_TOKEN_MESSAGE))
        } else {
            GoogleExchangeResult.Failed(DomainError.Validation(REJECTED_CODE, REJECTED_MESSAGE))
        }
}
