package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryCodeInputTest {

    @Test
    fun minusculasSePasanAMayusculas() {
        assertEquals("ABCD-EF12", RecoveryCodeInput.normalize("abcd-ef12"))
    }

    @Test
    fun espaciosSeRecortan() {
        assertEquals("ABCD-EF12", RecoveryCodeInput.normalize("  ABCD-EF12 \n"))
    }

    @Test
    fun ochoCaracteresSinGuionInsertanElGuion() {
        assertEquals("ABCD-EF12", RecoveryCodeInput.normalize("abcdef12"))
    }

    @Test
    fun conGuionSeAcepta() {
        assertEquals("1234-5678", RecoveryCodeInput.normalize("1234-5678"))
    }

    @Test
    fun sieteONueveCaracteres_devuelveNull() {
        assertNull(RecoveryCodeInput.normalize("ABCDEF1"))
        assertNull(RecoveryCodeInput.normalize("ABCDEF123"))
        assertNull(RecoveryCodeInput.normalize("ABC-DEF12"))
    }

    @Test
    fun simbolosDevuelvenNull() {
        assertNull(RecoveryCodeInput.normalize("AB!D-EF12"))
        assertNull(RecoveryCodeInput.normalize("ABCD_EF12"))
    }

    @Test
    fun vacioDevuelveNull() {
        assertNull(RecoveryCodeInput.normalize(""))
        assertNull(RecoveryCodeInput.normalize("   "))
    }
}
