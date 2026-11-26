package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.auth.RecoveryCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodesExportTest {

    private val codes = RecoveryCodes(listOf("CTPQ-Q479", "ABCD-EFGH", "WXYZ-2345"))

    @Test
    fun textoDelPortapapeles_unCodigoPorLinea() {
        assertEquals("CTPQ-Q479\nABCD-EFGH\nWXYZ-2345", RecoveryCodesExport.clipboardText(codes))
    }

    @Test
    fun textoDelPortapapeles_noIncluyeCabeceraNiCorreo() {
        val text = RecoveryCodesExport.clipboardText(codes)

        assertFalse(text.contains("Taskeer"))
        assertFalse(text.contains("@"))
    }

    @Test
    fun textoDelArchivo_tieneCabeceraYUnCodigoPorLinea() {
        val lines = RecoveryCodesExport.fileText(codes).lines()

        assertTrue(lines.first().startsWith("Códigos de recuperación de Taskeer"))
        assertEquals(codes.values, lines.filter { it in codes.values })
    }

    @Test
    fun textoDelArchivo_noIncluyeCorreoNiFecha() {
        val text = RecoveryCodesExport.fileText(codes)

        assertFalse(text.contains("@"))
        assertFalse(Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(text))
    }

    @Test
    fun nombreDelArchivo_esGenerico() {
        assertEquals("taskeer-codigos-recuperacion.txt", RecoveryCodesExport.FILE_NAME)
    }

    @Test
    fun lineasSeparadasPorSaltoDeLinea() {
        assertFalse(RecoveryCodesExport.fileText(codes).contains("\r"))
        assertEquals(3, RecoveryCodesExport.clipboardText(codes).lines().size)
    }
}
