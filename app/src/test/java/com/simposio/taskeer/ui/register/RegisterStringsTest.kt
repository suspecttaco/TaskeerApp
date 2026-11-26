package com.simposio.taskeer.ui.register

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterStringsTest {

    private val strings: Map<String, String> =
        Regex("<string name=\"(register_[a-z_]+)\">(.*?)</string>").findAll(MainSources.strings())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun textosNuevosEstanEnEspanolYConPrefijoRegister() {
        assertTrue(strings.size >= 30)
        assertEquals("Crear cuenta", strings.getValue("register_title"))
        assertEquals("¿No tienes cuenta? Crear cuenta", strings.getValue("register_link_action"))
        assertEquals("Mínimo 8 caracteres", strings.getValue("register_password_hint"))
        assertEquals("Salir sin guardar", strings.getValue("register_exit_leave"))
    }

    @Test
    fun noHayEmojisEnLosTextosNuevos() {
        val emoji = Regex("[\\x{2190}-\\x{2BFF}\\x{1F000}-\\x{1FAFF}]")

        assertTrue(strings.values.none { emoji.containsMatchIn(it) })
    }

    @Test
    fun elAvisoDeLosCodigosDiceUnaSolaVezYQueNoSePuedenVolverAVer() {
        val warning = strings.getValue("register_codes_warning")

        assertTrue(warning.contains("Cada código sirve una sola vez"))
        assertTrue(warning.contains("No volverás a verlos"))
        assertFalse(strings.getValue("register_error_email_taken").contains("ya está registrado"))
    }
}
