package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationInputTest {

    @Test
    fun urlConToken_devuelveElToken() {
        assertEquals(
            "abc123",
            VerificationInput.parseToken("https://app.example.com/verificar-correo?token=abc123"),
        )
    }

    @Test
    fun urlConTokenYOtrosParametros_devuelveElToken() {
        assertEquals(
            "abc123",
            VerificationInput.parseToken("https://app.example.com/verificar-correo?utm=1&token=abc123#x"),
        )
    }

    @Test
    fun urlSinToken_devuelveNull() {
        assertNull(VerificationInput.parseToken("https://app.example.com/verificar-correo?otro=1"))
        assertNull(VerificationInput.parseToken("https://app.example.com/verificar-correo"))
    }

    @Test
    fun urlConTokenVacio_devuelveNull() {
        assertNull(VerificationInput.parseToken("https://app.example.com/verificar-correo?token="))
    }

    @Test
    fun tokenSuelto_seDevuelveRecortado() {
        assertEquals("abc123", VerificationInput.parseToken("  abc123\n"))
    }

    @Test
    fun tokenCodificadoEnPorcentaje_seDecodifica() {
        assertEquals(
            "a+b/c=",
            VerificationInput.parseToken("https://app.example.com/verificar-correo?token=a%2Bb%2Fc%3D"),
        )
    }

    @Test
    fun codificacionInvalida_devuelveNull() {
        assertNull(VerificationInput.parseToken("https://app.example.com/v?token=%zz"))
    }

    @Test
    fun vacio_devuelveNull() {
        assertNull(VerificationInput.parseToken(""))
        assertNull(VerificationInput.parseToken("  \n "))
    }

    @Test
    fun espaciosInternos_devuelveNull() {
        assertNull(VerificationInput.parseToken("abc 123"))
    }

    @Test
    fun masDe512_devuelveNull() {
        assertNull(VerificationInput.parseToken("a".repeat(513)))
        assertEquals("a".repeat(512), VerificationInput.parseToken("a".repeat(512)))
    }
}
