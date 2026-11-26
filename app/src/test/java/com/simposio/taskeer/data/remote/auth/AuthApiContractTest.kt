package com.simposio.taskeer.data.remote.auth

import com.simposio.taskeer.domain.auth.AccountSummary
import org.json.JSONArray
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

    private fun created(body: String) = AuthApiContract.parseRegister(body) as AuthApiContract.RegisterResponse.Created

    @Test
    fun registerBody_incluyeCorreoPasswordYNombre() {
        val body = JSONObject(AuthApiContract.registerBody("Ana", "ana@example.com", "secreta1".toCharArray()))

        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("secreta1", body.getString("password"))
        assertEquals("Ana", body.getString("displayName"))
    }

    @Test
    fun parseRegister_conDiezCodigosYUsuario() {
        val codes = List(10) { "ABCD-000$it" }
        val json = JSONObject().put("user", JSONObject().put("email", "ana@example.com").put("displayName", "Ana"))
            .put("recoveryCodes", JSONArray(codes)).toString()

        val parsed = created(json)

        assertEquals(codes, parsed.codes)
        assertEquals(AccountSummary("ana@example.com", "Ana"), parsed.account)
    }

    @Test
    fun parseRegister_conMenosDeDiezCodigos_losConserva() {
        assertEquals(listOf("AAAA-BBBB", "CCCC-DDDD"), created("""{"recoveryCodes":["AAAA-BBBB","CCCC-DDDD"]}""").codes)
    }

    @Test
    fun parseRegister_descartaElementosEnBlancoOQueNoSonTexto() {
        val parsed = created("""{"recoveryCodes":["AAAA-BBBB","  ",7,null,{"a":1},"CCCC-DDDD"]}""")

        assertEquals(listOf("AAAA-BBBB", "CCCC-DDDD"), parsed.codes)
    }

    @Test
    fun parseRegister_recortaLosCodigosSinCambiarMayusculas() {
        assertEquals(listOf("abcd-Efgh"), created("""{"recoveryCodes":["  abcd-Efgh "]}""").codes)
    }

    @Test
    fun parseRegister_sinRecoveryCodes_devuelveListaVacia() {
        assertTrue(created("""{"user":{"email":"ana@example.com"}}""").codes.isEmpty())
    }

    @Test
    fun parseRegister_jsonRoto_esUnreadable() {
        assertEquals(AuthApiContract.RegisterResponse.Unreadable, AuthApiContract.parseRegister("<html>"))
        assertEquals(AuthApiContract.RegisterResponse.Unreadable, AuthApiContract.parseRegister(""))
    }

    @Test
    fun parseRegister_sinUsuario_cuentaNula() {
        assertNull(created("""{"recoveryCodes":["AAAA-BBBB"]}""").account)
    }

    @Test
    fun parseRegister_toString_noExponeCodigos() {
        val rendered = created("""{"recoveryCodes":["AAAA-BBBB"]}""").toString()

        assertFalse(rendered.contains("AAAA"))
    }
}
