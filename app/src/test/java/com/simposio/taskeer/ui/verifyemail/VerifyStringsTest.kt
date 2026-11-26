package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyStringsTest {

    private val strings: Map<String, String> =
        Regex("<string name=\"(verify_[a-z_]+)\">(.*?)</string>").findAll(MainSources.strings())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun textosNuevosEstanEnEspanolYConPrefijoVerify() {
        assertEquals("Verificar correo", strings.getValue("verify_title"))
        assertEquals("Enlace o código del correo", strings.getValue("verify_input_label"))
        assertEquals("Pega el enlace o el código del correo", strings.getValue("verify_error_input_invalid"))
        assertEquals("Tu correo quedó verificado", strings.getValue("verify_done_message"))
        assertEquals(
            "Este enlace ya se usó o venció. Pide uno nuevo desde Ajustes → Cuenta.",
            strings.getValue("verify_error_invalid_or_expired"),
        )
        assertEquals("Reenviar correo de verificación", strings.getValue("verify_resend_action"))
        assertEquals("Tengo un enlace", strings.getValue("verify_have_link"))
    }

    @Test
    fun noHayEmojis() {
        val emoji = Regex("[\\x{2600}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}]")

        assertTrue(strings.values.none { emoji.containsMatchIn(it) })
    }
}
