package com.simposio.taskeer.ui.passwordreset

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordResetStringsTest {

    private val strings: Map<String, String> =
        Regex("<string name=\"((?:reset|chpw|regen)_[a-z_]+)\">(.*?)</string>").findAll(MainSources.strings())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun textosEnEspanolYConPrefijos() {
        assertTrue(strings.size >= 50)
        assertEquals("Restablecer contraseña", strings.getValue("reset_title"))
        assertEquals("Correo o código de recuperación incorrectos", strings.getValue("reset_error_incorrect"))
        assertEquals("Cambiar contraseña", strings.getValue("chpw_title"))
        assertEquals("La contraseña actual no es correcta", strings.getValue("chpw_error_current_incorrect"))
        assertEquals("Regenerar códigos", strings.getValue("regen_title"))
        assertEquals("Contraseña cambiada. Inicia sesión con la nueva", strings.getValue("reset_login_notice"))
        assertTrue(strings.getValue("regen_warning").contains("incluso los que no has usado"))
        assertTrue(strings.getValue("reset_login_notice_codes").contains("los demás siguen siendo válidos"))
    }

    @Test
    fun noHayEmojis() {
        val emoji = Regex("[\\x{2190}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}]")

        assertTrue(strings.values.none { emoji.containsMatchIn(it) })
    }

    @Test
    fun elMensajeDeCodigoIncorrectoNoDistingueCorreoDeCodigo() {
        val message = strings.getValue("reset_error_incorrect").lowercase()

        assertTrue(message.contains("correo o código"))
        assertTrue(!message.contains("no existe") && !message.contains("ya se usó") && !message.contains("no encontr"))
    }
}
