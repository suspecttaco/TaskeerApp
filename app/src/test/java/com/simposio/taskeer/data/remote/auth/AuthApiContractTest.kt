package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiContractTest {

    @Test
    fun loginBody_incluyeEmailYPassword() {
        val body = JSONObject(AuthApiContract.loginBody("ana@example.com", "secreta".toCharArray()))

        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("secreta", body.getString("password"))
    }

    @Test
    fun parseLogin_conTokensYUsuario() {
        val parsed = AuthApiContract.parseLogin(
            """{"accessToken":"a1","refreshToken":"r1","user":{"email":"ana@example.com","displayName":"Ana","id":"x"}}""",
        ) as AuthApiContract.LoginResponse.Tokens

        assertEquals("a1", parsed.accessToken)
        assertEquals("r1", parsed.refreshToken)
        assertEquals(AccountSummary("ana@example.com", "Ana"), parsed.account)
    }

    @Test
    fun parseLogin_sinRefreshToken_esNoRefreshToken() {
        val parsed = AuthApiContract.parseLogin("""{"accessToken":"a1","user":{"email":"ana@example.com"}}""")

        assertEquals(AuthApiContract.LoginResponse.NoRefreshToken, parsed)
    }

    @Test
    fun parseLogin_sinAccessToken_esNull() {
        assertNull(AuthApiContract.parseLogin("""{"refreshToken":"r1"}"""))
    }

    @Test
    fun parseLogin_jsonRoto_esNull() {
        assertNull(AuthApiContract.parseLogin("<html>"))
        assertNull(AuthApiContract.parseLogin(""))
    }

    @Test
    fun logoutBody_incluyeElRefreshToken() {
        val body = JSONObject(AuthApiContract.logoutBody("r1"))

        assertEquals("r1", body.getString("refreshToken"))
    }

    @Test
    fun toString_noExponeSecretos() {
        val parsed = AuthApiContract.parseLogin(
            """{"accessToken":"a-secreto","refreshToken":"r-secreto","user":{"email":"ana@example.com"}}""",
        )

        assertFalse(parsed.toString().contains("secreto"))
        assertTrue(parsed.toString().contains("ana@example.com"))
    }
}
