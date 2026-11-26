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
    val rotating = FakeRotatingBackend(grace = true)

    override suspend fun send(request: HttpRequest): TransportResult {
        requests += request
        return when {
            request.url.endsWith("auth/login") -> login(request)
            request.url.endsWith("auth/logout") -> logout(request)
            request.url.endsWith("auth/register") -> register(request)
            request.url.endsWith("auth/refresh") -> refresh(request)
            else -> response(404, errorBody("ROUTE_NOT_FOUND"))
        }
    }

    private fun login(request: HttpRequest): TransportResult {
        val body = JSONObject(requireNotNull(request.body))
        if (body.getString("email") != EMAIL || body.getString("password") != PASSWORD) {
            return response(401, errorBody("INVALID_CREDENTIALS"))
        }
        val user = JSONObject().put("email", EMAIL).put("displayName", "Ana")
        val payload = JSONObject().put("accessToken", "access-login").put("user", user)
        if (b8) {
            rotating.seed("access-login", "refresh-login")
            payload.put("refreshToken", "refresh-login")
        }
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
        val REGISTER_CODES = List(10) { "ABCD-%04d".format(it) }
    }
}
