package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
