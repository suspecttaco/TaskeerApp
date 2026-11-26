package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Contrato B8 provisional: ruta y claves JSON se confirmarán con el backend.
// Es el único sitio que conoce la forma del login, el logout y el registro.
internal object AuthApiContract {
    const val LOGIN_PATH = "auth/login"
    const val LOGOUT_PATH = "auth/logout"
    const val REGISTER_PATH = "auth/register"

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

    sealed interface RegisterResponse {
        class Created(val account: AccountSummary?, val codes: List<String>) : RegisterResponse {
            override fun toString(): String = "Created(account=$account, codes=${codes.size})"
        }

        data object Unreadable : RegisterResponse
    }

    fun loginBody(email: String, password: CharArray): String =
        JSONObject().put("email", email).put("password", String(password)).toString()

    fun registerBody(displayName: String, email: String, password: CharArray): String =
        JSONObject().put("email", email).put("password", String(password)).put("displayName", displayName).toString()

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

    // Los códigos se aceptan tal cual (recortados): el servidor ya guardó sus hashes y rechazarlos dejaría la cuenta sin recuperación.
    fun parseRegister(body: String): RegisterResponse = try {
        val json = JSONObject(body)
        val user = json.optJSONObject("user")
        val email = user?.optString("email").orEmpty()
        val displayName = user?.optString("displayName").orEmpty()
        RegisterResponse.Created(
            account = if (user == null || email.isBlank()) null else AccountSummary(email, displayName),
            codes = codesOf(json.optJSONArray("recoveryCodes")),
        )
    } catch (_: JSONException) {
        RegisterResponse.Unreadable
    }

    private fun codesOf(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
