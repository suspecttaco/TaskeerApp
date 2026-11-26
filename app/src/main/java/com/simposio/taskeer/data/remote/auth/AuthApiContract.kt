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
    const val VERIFY_EMAIL_PATH = "auth/verify-email"
    const val RESEND_VERIFICATION_PATH = "auth/resend-verification"
    const val RESET_PASSWORD_PATH = "auth/reset-password-with-code"
    const val CHANGE_PASSWORD_PATH = "auth/change-password"
    const val REGENERATE_CODES_PATH = "auth/recovery-codes/regenerate"
    const val GOOGLE_PATH = "auth/google/mobile"

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

    sealed interface GoogleResponse {
        data object NoRefreshToken : GoogleResponse

        data object Unreadable : GoogleResponse

        class Tokens(
            val accessToken: String,
            val refreshToken: String,
            val account: AccountSummary,
            val codes: List<String>,
        ) : GoogleResponse {
            override fun toString(): String = "Tokens(account=$account, codes=${codes.size})"
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

    fun googleBody(idToken: String, nonce: String): String =
        JSONObject().put("idToken", idToken).put("nonce", nonce).toString()

    fun registerBody(displayName: String, email: String, password: CharArray): String =
        JSONObject().put("email", email).put("password", String(password)).put("displayName", displayName).toString()

    fun resetBody(email: String, code: String, newPassword: CharArray): String =
        JSONObject().put("email", email).put("code", code).put("newPassword", String(newPassword)).toString()

    fun changePasswordBody(current: CharArray, new: CharArray): String =
        JSONObject().put("currentPassword", String(current)).put("newPassword", String(new)).toString()

    fun regenerateBody(current: CharArray): String =
        JSONObject().put("currentPassword", String(current)).toString()

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
                        emailVerified = emailVerifiedOf(user),
                        hasPassword = hasPasswordOf(user),
                    ),
                )
            }
        }
    } catch (_: JSONException) {
        null
    }

    // `recoveryCodes` ausente o ilegible es lista vacía: solo el servidor sabe si esta sesión creó la cuenta.
    fun parseGoogle(body: String): GoogleResponse = when (val login = parseLogin(body)) {
        null -> GoogleResponse.Unreadable
        LoginResponse.NoRefreshToken -> GoogleResponse.NoRefreshToken
        is LoginResponse.Tokens -> GoogleResponse.Tokens(
            accessToken = login.accessToken,
            refreshToken = login.refreshToken,
            account = login.account,
            codes = try {
                codesOf(JSONObject(body).optJSONArray("recoveryCodes"))
            } catch (_: JSONException) {
                emptyList()
            },
        )
    }

    // Los códigos se aceptan tal cual (recortados): el servidor ya guardó sus hashes y rechazarlos dejaría la cuenta sin recuperación.
    fun parseRegister(body: String): RegisterResponse = try {
        val json = JSONObject(body)
        val user = json.optJSONObject("user")
        val email = user?.optString("email").orEmpty()
        val displayName = user?.optString("displayName").orEmpty()
        RegisterResponse.Created(
            account = if (user == null || email.isBlank()) null else AccountSummary(email, displayName, emailVerifiedOf(user), hasPasswordOf(user)),
            codes = codesOf(json.optJSONArray("recoveryCodes")),
        )
    } catch (_: JSONException) {
        RegisterResponse.Unreadable
    }

    // null = ilegible; lista vacía = respuesta legible sin códigos.
    fun parseRecoveryCodes(body: String): List<String>? = try {
        JSONObject(body).let { codesOf(it.optJSONArray("recoveryCodes")) }
    } catch (_: JSONException) {
        null
    }

    fun parseVerified(body: String): Boolean = try {
        JSONObject(body).optBoolean("verified", false)
    } catch (_: JSONException) {
        false
    }

    fun parseAlreadyVerified(body: String): Boolean? = try {
        JSONObject(body).takeIf { it.has("alreadyVerified") }?.optBoolean("alreadyVerified")
    } catch (_: JSONException) {
        null
    }

    // Ausente = desconocido; nulo explícito = sin verificar.
    private fun emailVerifiedOf(user: JSONObject?): Boolean? =
        if (user == null || !user.has("emailVerifiedAt")) null else !user.isNull("emailVerifiedAt")

    private fun hasPasswordOf(user: JSONObject?): Boolean? =
        if (user == null || user.isNull("hasPassword") || !user.has("hasPassword")) null else user.optBoolean("hasPassword")

    private fun codesOf(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
