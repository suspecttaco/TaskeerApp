package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import org.json.JSONException
import org.json.JSONObject

// Contrato B8 provisional: ruta y claves JSON se confirmarán con el backend.
// Es el único sitio que conoce la forma del login y del logout.
internal object AuthApiContract {
    const val LOGIN_PATH = "auth/login"
    const val LOGOUT_PATH = "auth/logout"

    sealed interface LoginResponse {
        // El backend sin B8 responde 200 con el refresh solo en cookie.
        data object NoRefreshToken : LoginResponse

        class Tokens(
            val accessToken: String,
            val refreshToken: String,
            val account: AccountSummary,
        ) : LoginResponse {
            override fun toString(): String = "Tokens(account=$account)"
        }
    }

    fun loginBody(email: String, password: CharArray): String =
        JSONObject().put("email", email).put("password", String(password)).toString()

    fun logoutBody(refreshToken: String): String =
        JSONObject().put("refreshToken", refreshToken).toString()

    fun parseLogin(body: String): LoginResponse? = try {
        val json = JSONObject(body)
        val accessToken = json.optString("accessToken")
        if (accessToken.isBlank()) {
            null
        } else {
            val refreshToken = json.optString("refreshToken")
            if (refreshToken.isBlank()) {
                LoginResponse.NoRefreshToken
            } else {
                val user = json.optJSONObject("user")
                LoginResponse.Tokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    account = AccountSummary(
                        email = user?.optString("email").orEmpty(),
                        displayName = user?.optString("displayName").orEmpty(),
                    ),
                )
            }
        }
    } catch (_: JSONException) {
        null
    }
}
