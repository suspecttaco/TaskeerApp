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

    @Test
    fun parseVerified_conVerifiedTrue_esTrue() {
        assertTrue(AuthApiContract.parseVerified("""{"verified":true}"""))
    }

    @Test
    fun parseVerified_conVerifiedFalseOSinClave_esFalse() {
        assertFalse(AuthApiContract.parseVerified("""{"verified":false}"""))
        assertFalse(AuthApiContract.parseVerified("""{}"""))
    }

    @Test
    fun parseVerified_jsonRoto_esFalse() {
        assertFalse(AuthApiContract.parseVerified("<html>"))
        assertFalse(AuthApiContract.parseVerified(""))
    }

    @Test
    fun parseAlreadyVerified_distingueTrueFalseEIlegible() {
        assertEquals(true, AuthApiContract.parseAlreadyVerified("""{"alreadyVerified":true}"""))
        assertEquals(false, AuthApiContract.parseAlreadyVerified("""{"alreadyVerified":false}"""))
        assertNull(AuthApiContract.parseAlreadyVerified("""{}"""))
        assertNull(AuthApiContract.parseAlreadyVerified("<html>"))
    }

    private fun loginWith(user: String) =
        AuthApiContract.parseLogin("""{"accessToken":"a","refreshToken":"r","user":$user}""")
            as AuthApiContract.LoginResponse.Tokens

    @Test
    fun parseLogin_conEmailVerifiedAtPresente_esVerificado() {
        assertEquals(true, loginWith("""{"email":"a@b.co","emailVerifiedAt":"2026-11-01T00:00:00Z"}""").account.emailVerified)
    }

    @Test
    fun parseLogin_conEmailVerifiedAtNulo_noEstaVerificado() {
        assertEquals(false, loginWith("""{"email":"a@b.co","emailVerifiedAt":null}""").account.emailVerified)
    }

    @Test
    fun parseLogin_sinEmailVerifiedAt_esDesconocido() {
        assertNull(loginWith("""{"email":"a@b.co"}""").account.emailVerified)
    }

    private fun registerWith(user: String) =
        (AuthApiContract.parseRegister("""{"user":$user,"recoveryCodes":["A"]}""")
            as AuthApiContract.RegisterResponse.Created).account

    @Test
    fun parseRegister_conEmailVerifiedAt_derivaElEstado() {
        assertEquals(true, registerWith("""{"email":"a@b.co","emailVerifiedAt":"2026-11-01T00:00:00Z"}""")?.emailVerified)
        assertEquals(false, registerWith("""{"email":"a@b.co","emailVerifiedAt":null}""")?.emailVerified)
        assertNull(registerWith("""{"email":"a@b.co"}""")?.emailVerified)
    }

    @Test
    fun resetBody_llevaEmailCodeYNewPassword() {
        val body = JSONObject(AuthApiContract.resetBody("ana@example.com", "ABCD-EF12", "nueva-clave".toCharArray()))

        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("ABCD-EF12", body.getString("code"))
        assertEquals("nueva-clave", body.getString("newPassword"))
    }

    @Test
    fun changePasswordBody_llevaActualYNueva() {
        val body = JSONObject(AuthApiContract.changePasswordBody("vieja".toCharArray(), "nueva-clave".toCharArray()))

        assertEquals("vieja", body.getString("currentPassword"))
        assertEquals("nueva-clave", body.getString("newPassword"))
    }

    @Test
    fun regenerateBody_llevaSoloLaContrasenaActual() {
        val body = JSONObject(AuthApiContract.regenerateBody("vieja".toCharArray()))

        assertEquals("vieja", body.getString("currentPassword"))
        assertEquals(1, body.length())
    }

    @Test
    fun parseRecoveryCodes_conDiezCodigos_losDevuelve() {
        val codes = List(10) { "ABCD-000$it" }

        assertEquals(codes, AuthApiContract.parseRecoveryCodes(JSONObject().put("recoveryCodes", JSONArray(codes)).toString()))
    }

    @Test
    fun parseRecoveryCodes_sinClaveOVacio_esListaVacia() {
        assertEquals(emptyList<String>(), AuthApiContract.parseRecoveryCodes("""{}"""))
        assertEquals(emptyList<String>(), AuthApiContract.parseRecoveryCodes("""{"recoveryCodes":[]}"""))
    }

    @Test
    fun parseRecoveryCodes_ilegible_esNull() {
        assertNull(AuthApiContract.parseRecoveryCodes("<html>"))
    }

    @Test
    fun parseRecoveryCodes_descartaLoQueNoSonCadenas() {
        assertEquals(listOf("AAAA-BBBB"), AuthApiContract.parseRecoveryCodes("""{"recoveryCodes":[7,null,"AAAA-BBBB"," "]}"""))
    }

    @Test
    fun parseLogin_conHasPassword_losDistingue() {
        fun hasPassword(user: String) = (AuthApiContract.parseLogin(
            """{"accessToken":"a","refreshToken":"r","user":$user}""",
        ) as AuthApiContract.LoginResponse.Tokens).account.hasPassword

        assertEquals(false, hasPassword("""{"email":"a@b.co","hasPassword":false}"""))
        assertEquals(true, hasPassword("""{"email":"a@b.co","hasPassword":true}"""))
        assertNull(hasPassword("""{"email":"a@b.co"}"""))
    }

    @Test
    fun parseRegister_conHasPassword_loConserva() {
        val parsed = created("""{"user":{"email":"a@b.co","displayName":"Ana","hasPassword":true},"recoveryCodes":["AAAA-BBBB"]}""")

        assertEquals(true, parsed.account?.hasPassword)
    }
}
