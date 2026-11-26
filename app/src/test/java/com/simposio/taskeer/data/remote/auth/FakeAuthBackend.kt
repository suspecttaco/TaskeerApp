package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.data.remote.FakeRotatingBackend
import com.simposio.taskeer.data.remote.HttpRequest
import com.simposio.taskeer.data.remote.HttpTransport
import com.simposio.taskeer.data.remote.TransportResult
import com.simposio.taskeer.data.remote.errorBody
import com.simposio.taskeer.data.remote.response
import org.json.JSONArray
import org.json.JSONObject

// Con b8 = false imita al backend de hoy: el login fija el refresh en cookie y refresh/logout exigen Origin.
class FakeAuthBackend(private val b8: Boolean) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    val revokedRefreshTokens = mutableListOf<String>()
    val registeredEmails = mutableListOf<String>()
    val validVerificationTokens = mutableSetOf<String>()
    var verificationEnabled = true
    var resendAlreadyVerified = false
    val rotating = FakeRotatingBackend(grace = true)

    // Modelo de servidor: 10 códigos de un solo uso, regeneración que borra los anteriores y revocación de refresh.
    var password = PASSWORD
    var changeReturnsRefresh = true
    val usedCodes = mutableSetOf<String>()
    var recoveryCodes = REGISTER_CODES.toMutableList()
    // Modelo de servidor de Google (el cliente nunca lo decide): vincula solo si la cuenta local está verificada,
    // crea sin contraseña si no existe y rechaza el login de correo de las cuentas sin contraseña.
    var googleEndpoint = true
    val verifiedLocalEmails = mutableSetOf(EMAIL)
    val unverifiedLocalEmails = mutableSetOf<String>()
    val linkedGoogleEmails = mutableSetOf<String>()
    val googleOnlyEmails = mutableSetOf<String>()
    var googleOnlyLoginRejection = "INVALID_CREDENTIALS"
    private val issuedRefreshTokens = mutableListOf<String>()
    private var currentAccess = "access-login"

    override suspend fun send(request: HttpRequest): TransportResult {
        requests += request
        return when {
            request.url.endsWith("auth/login") -> login(request)
            request.url.endsWith("auth/google/mobile") -> google(request)
            request.url.endsWith("auth/logout") -> logout(request)
            request.url.endsWith("auth/register") -> register(request)
            request.url.contains("auth/verify-email") -> verifyEmail(request)
            request.url.endsWith("auth/resend-verification") -> resendVerification(request)
            request.url.endsWith("auth/reset-password-with-code") -> resetPassword(request)
            request.url.endsWith("auth/change-password") -> changePassword(request)
            request.url.endsWith("auth/recovery-codes/regenerate") -> regenerateCodes(request)
            request.url.endsWith("auth/refresh") -> refresh(request)
            else -> response(404, errorBody("ROUTE_NOT_FOUND"))
        }
    }

    private fun login(request: HttpRequest): TransportResult {
        val body = JSONObject(requireNotNull(request.body))
        if (body.getString("email").lowercase() in googleOnlyEmails) {
            return response(401, errorBody(googleOnlyLoginRejection))
        }
        if (body.getString("email") != EMAIL || body.getString("password") != password) {
            return response(401, errorBody("INVALID_CREDENTIALS"))
        }
        val user = JSONObject().put("email", EMAIL).put("displayName", "Ana")
        val payload = JSONObject().put("accessToken", "access-login").put("user", user)
        if (b8) {
            rotating.seed("access-login", "refresh-login")
            currentAccess = "access-login"
            issuedRefreshTokens += "refresh-login"
            payload.put("refreshToken", "refresh-login")
        }
        return response(200, payload.toString())
    }

    private fun google(request: HttpRequest): TransportResult {
        if (!googleEndpoint) return response(404, errorBody("ROUTE_NOT_FOUND"))
        val body = JSONObject(requireNotNull(request.body))
        if (body.optString("nonce").isBlank()) return response(400, errorBody("INVALID_OAUTH_STATE"))
        val idToken = body.optString("idToken")
        if (!idToken.startsWith(GOOGLE_TOKEN_PREFIX)) return response(401, errorBody("INVALID_GOOGLE_TOKEN"))
        val email = idToken.removePrefix(GOOGLE_TOKEN_PREFIX).lowercase()
        var created = false
        when {
            email in linkedGoogleEmails || email in googleOnlyEmails -> Unit
            email in unverifiedLocalEmails -> return response(409, errorBody("LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED"))
            email in verifiedLocalEmails -> linkedGoogleEmails += email
            else -> {
                googleOnlyEmails += email
                created = true
            }
        }
        val user = JSONObject().put("email", email).put("displayName", "Ana")
            .put("emailVerifiedAt", "2026-11-26T00:00:00.000Z").put("hasPassword", email !in googleOnlyEmails)
        val payload = JSONObject().put("accessToken", "access-google").put("user", user)
        if (b8) {
            rotating.seed("access-google", "refresh-google")
            currentAccess = "access-google"
            issuedRefreshTokens += "refresh-google"
            payload.put("refreshToken", "refresh-google")
        }
        if (created) payload.put("recoveryCodes", JSONArray(REGISTER_CODES))
        return response(200, payload.toString())
    }

    private fun register(request: HttpRequest): TransportResult {
        val body = JSONObject(requireNotNull(request.body))
        val email = body.getString("email").lowercase()
        if (email in registeredEmails || email == EMAIL) return response(409, errorBody("EMAIL_ALREADY_REGISTERED"))
        registeredEmails += email
        val user = JSONObject().put("email", email).put("displayName", body.getString("displayName"))
        val codes = JSONArray(REGISTER_CODES)
        return response(201, JSONObject().put("user", user).put("recoveryCodes", codes).toString())
    }

    private fun verifyEmail(request: HttpRequest): TransportResult {
        val token = request.url.substringAfter("token=", "")
        if (!validVerificationTokens.remove(token)) return response(400, errorBody("INVALID_OR_EXPIRED_TOKEN"))
        return response(200, JSONObject().put("verified", true).toString())
    }

    private fun resendVerification(request: HttpRequest): TransportResult {
        if (request.headers["Authorization"] != "Bearer access-login") return response(401, errorBody("UNAUTHENTICATED"))
        if (resendAlreadyVerified) return response(200, JSONObject().put("alreadyVerified", true).toString())
        if (!verificationEnabled) return response(503, errorBody("EMAIL_VERIFICATION_DISABLED"))
        return response(200, JSONObject().put("alreadyVerified", false).toString())
    }

    private fun resetPassword(request: HttpRequest): TransportResult {
        val body = JSONObject(requireNotNull(request.body))
        val code = body.getString("code")
        if (body.getString("email") != EMAIL || code !in recoveryCodes || code in usedCodes) {
            return response(400, errorBody("INVALID_RECOVERY_CODE"))
        }
        usedCodes += code
        password = body.getString("newPassword")
        revokedRefreshTokens += issuedRefreshTokens
        return response(200, JSONObject().put("reset", true).toString())
    }

    private fun changePassword(request: HttpRequest): TransportResult {
        if (request.headers["Authorization"] != "Bearer $currentAccess") return response(401, errorBody("UNAUTHENTICATED"))
        val body = JSONObject(requireNotNull(request.body))
        if (body.getString("currentPassword") != password) return response(401, errorBody("INVALID_CREDENTIALS"))
        password = body.getString("newPassword")
        revokedRefreshTokens += issuedRefreshTokens
        val user = JSONObject().put("email", EMAIL).put("displayName", "Ana").put("hasPassword", true)
        val payload = JSONObject().put("accessToken", "access-change").put("user", user)
        currentAccess = "access-change"
        if (changeReturnsRefresh) {
            rotating.seed("access-change", "refresh-change")
            issuedRefreshTokens += "refresh-change"
            payload.put("refreshToken", "refresh-change")
        }
        return response(200, payload.toString())
    }

    private fun regenerateCodes(request: HttpRequest): TransportResult {
        if (request.headers["Authorization"] != "Bearer $currentAccess") return response(401, errorBody("UNAUTHENTICATED"))
        if (JSONObject(requireNotNull(request.body)).getString("currentPassword") != password) {
            return response(401, errorBody("INVALID_CREDENTIALS"))
        }
        usedCodes.clear()
        recoveryCodes = List(10) { "NEW1-%04d".format(it) }.toMutableList()
        return response(200, JSONObject().put("recoveryCodes", JSONArray(recoveryCodes)).toString())
    }

    private fun logout(request: HttpRequest): TransportResult {
        if (!b8) return response(403, errorBody("UNTRUSTED_ORIGIN"))
        revokedRefreshTokens += JSONObject(requireNotNull(request.body)).getString("refreshToken")
        return response(200, JSONObject().put("loggedOut", true).toString())
    }

    private suspend fun refresh(request: HttpRequest): TransportResult {
        if (!b8) return response(403, errorBody("UNTRUSTED_ORIGIN"))
        val presented = JSONObject(requireNotNull(request.body)).getString("refreshToken")
        if (presented in revokedRefreshTokens) return response(401, errorBody("INVALID_REFRESH_TOKEN"))
        return rotating.send(request)
    }

    companion object {
        const val EMAIL = "ana@example.com"
        const val PASSWORD = "secreta"
        const val GOOGLE_TOKEN_PREFIX = "google-token:"

        fun googleIdToken(email: String) = GOOGLE_TOKEN_PREFIX + email
        val REGISTER_CODES = List(10) { "ABCD-%04d".format(it) }
    }
}
