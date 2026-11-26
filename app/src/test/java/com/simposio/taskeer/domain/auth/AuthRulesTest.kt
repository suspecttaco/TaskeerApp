package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

    private fun code(email: String, password: String = "secreta") =
        AuthRules.validateCredentials(email, password.toCharArray())?.code

    @Test
    fun emailVacio_esEmailInvalid() {
        assertEquals("EMAIL_INVALID", code(""))
        assertEquals("EMAIL_INVALID", code("   "))
    }

    @Test
    fun emailSinArroba_esEmailInvalid() {
        assertEquals("EMAIL_INVALID", code("ana.example.com"))
    }

    @Test
    fun emailSinDominioConPunto_esEmailInvalid() {
        assertEquals("EMAIL_INVALID", code("ana@example"))
        assertEquals("EMAIL_INVALID", code("ana@.com"))
    }

    @Test
    fun emailSeRecortaAntesDeValidar() {
        assertNull(code("  ana@example.com  "))
    }

    @Test
    fun emailMayorDe254_esEmailInvalid() {
        val local = "a".repeat(AuthRules.EMAIL_MAX - "@example.com".length + 1)

        assertEquals("EMAIL_INVALID", code("$local@example.com"))
        assertNull(code("${local.dropLast(1)}@example.com"))
    }

    @Test
    fun passwordVacia_esPasswordRequired() {
        assertEquals("PASSWORD_REQUIRED", code("ana@example.com", ""))
    }

    @Test
    fun passwordSoloEspacios_esValida_yNoSeRecorta() {
        val password = "   ".toCharArray()

        assertNull(AuthRules.validateCredentials("ana@example.com", password))
        assertEquals(3, password.size)
    }

    @Test
    fun passwordMayorDe128_esPasswordTooLong() {
        assertEquals("PASSWORD_TOO_LONG", code("ana@example.com", "x".repeat(129)))
        assertNull(code("ana@example.com", "x".repeat(128)))
    }

    @Test
    fun credencialesValidas_devuelvenNull() {
        assertNull(AuthRules.validateCredentials("ana@example.com", "secreta".toCharArray()))
    }

    @Test
    fun normalizedEmail_soloHaceTrim_yNoPasaAMinusculas() {
        assertEquals("Ana@Example.COM", AuthRules.normalizedEmail("  Ana@Example.COM \n"))
    }

    @Test
    fun retryMinutes_redondeaAlAlza_conMinimoUno() {
        assertEquals(1, AuthRules.retryMinutes(0))
        assertEquals(1, AuthRules.retryMinutes(1))
        assertEquals(1, AuthRules.retryMinutes(60))
        assertEquals(2, AuthRules.retryMinutes(61))
        assertEquals(15, AuthRules.retryMinutes(900))
    }

    @Test
    fun retryMinutes_sinDato_esNull() {
        assertNull(AuthRules.retryMinutes(null))
    }

    @Test
    fun lockoutSeconds_seAcotaEntre1y900() {
        assertEquals(1L, AuthRules.lockoutSeconds(0))
        assertEquals(30L, AuthRules.lockoutSeconds(30))
        assertEquals(900L, AuthRules.lockoutSeconds(5_000))
    }

    @Test
    fun lockoutSeconds_sinDato_esCero() {
        assertEquals(0L, AuthRules.lockoutSeconds(null))
    }

    private fun registration(
        name: String = "Ana",
        email: String = "ana@example.com",
        password: String = "secreta1",
    ) = AuthRules.validateRegistration(name, email, password.toCharArray())?.code

    @Test
    fun registro_nombreCorto_esDisplayNameInvalid() {
        assertEquals("DISPLAY_NAME_INVALID", registration(name = "A"))
        assertEquals("DISPLAY_NAME_INVALID", registration(name = ""))
    }

    @Test
    fun registro_nombreSeRecortaAntesDeValidar() {
        assertEquals("DISPLAY_NAME_INVALID", registration(name = "  A  "))
        assertNull(registration(name = "  Al  "))
    }

    @Test
    fun registro_nombreMayorDe100_esDisplayNameInvalid() {
        assertEquals("DISPLAY_NAME_INVALID", registration(name = "a".repeat(101)))
        assertNull(registration(name = "a".repeat(100)))
    }

    @Test
    fun registro_correoInvalido_esEmailInvalid() {
        assertEquals("EMAIL_INVALID", registration(email = "ana.example.com"))
    }

    @Test
    fun registro_passwordDe7_esPasswordTooShort() {
        assertEquals("PASSWORD_TOO_SHORT", registration(password = "1234567"))
    }

    @Test
    fun registro_passwordVacia_esPasswordTooShort() {
        assertEquals("PASSWORD_TOO_SHORT", registration(password = ""))
    }

    @Test
    fun registro_passwordDe8_esValida() {
        assertNull(registration(password = "12345678"))
    }

    @Test
    fun registro_passwordSoloEspaciosDe8_esValidaYNoSeRecorta() {
        val password = " ".repeat(8).toCharArray()

        assertNull(AuthRules.validateRegistration("Ana", "ana@example.com", password))
        assertEquals(8, password.size)
    }

    @Test
    fun registro_passwordDe129_esPasswordTooLong() {
        assertEquals("PASSWORD_TOO_LONG", registration(password = "x".repeat(129)))
        assertNull(registration(password = "x".repeat(128)))
    }

    @Test
    fun registro_ordenDeErrores_nombreCorreoPassword() {
        assertEquals("DISPLAY_NAME_INVALID", registration(name = "A", email = "x", password = "1"))
        assertEquals("EMAIL_INVALID", registration(email = "x", password = "1"))
        assertEquals("PASSWORD_TOO_SHORT", registration(password = "1"))
    }

    @Test
    fun registro_datosValidos_devuelvenNull() {
        assertNull(registration())
    }

    @Test
    fun passwordsMatch_iguales_esTrue() {
        assertTrue(AuthRules.passwordsMatch("secreta1".toCharArray(), "secreta1".toCharArray()))
    }

    @Test
    fun passwordsMatch_distintas_esFalse() {
        assertFalse(AuthRules.passwordsMatch("secreta1".toCharArray(), "secreta2".toCharArray()))
    }

    @Test
    fun passwordsMatch_distintaLongitud_esFalse() {
        assertFalse(AuthRules.passwordsMatch("secreta1".toCharArray(), "secreta".toCharArray()))
    }

    @Test
    fun validateNewPassword_menosDe8_esPasswordTooShort() {
        val error = AuthRules.validateNewPassword("1234567".toCharArray(), "1234567".toCharArray())

        assertEquals(AuthRules.PASSWORD_TOO_SHORT, error?.code)
    }

    @Test
    fun validateNewPassword_masDe128_esPasswordTooShort() {
        val long = "a".repeat(129).toCharArray()

        assertEquals(AuthRules.PASSWORD_TOO_SHORT, AuthRules.validateNewPassword(long, long.copyOf())?.code)
    }

    @Test
    fun validateNewPassword_noCoincide_esPasswordMismatch() {
        val error = AuthRules.validateNewPassword("12345678".toCharArray(), "12345679".toCharArray())

        assertEquals(AuthRules.PASSWORD_MISMATCH, error?.code)
    }

    @Test
    fun validateNewPassword_noRecortaEspacios() {
        assertNull(AuthRules.validateNewPassword("  abcd  ".toCharArray(), "  abcd  ".toCharArray()))
        assertEquals(
            AuthRules.PASSWORD_MISMATCH,
            AuthRules.validateNewPassword("  abcd  ".toCharArray(), "abcd".padEnd(8, 'x').toCharArray())?.code,
        )
    }

    @Test
    fun validateChangePassword_actualVacia_esPasswordRequired() {
        val error = AuthRules.validateChangePassword(charArrayOf(), "12345678".toCharArray(), "12345678".toCharArray())

        assertEquals(AuthRules.PASSWORD_REQUIRED, error?.code)
    }
}
