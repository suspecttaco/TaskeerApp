package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleStringsTest {

    private fun text(name: String): String? =
        Regex("name=\"$name\">(.*?)</string>").find(MainSources.strings())?.groupValues?.get(1)

    @Test
    fun losMensajesDeR8SonLosEspecificados() {
        assertEquals("Continuar con Google", text("google_continue_action"))
        assertEquals(
            "No hay ninguna cuenta de Google en este dispositivo. Añade una e inténtalo de nuevo",
            text("google_error_no_account"),
        )
        assertEquals("Google no está disponible en este dispositivo", text("google_error_unavailable"))
        assertEquals("No se pudo obtener la cuenta de Google. Inténtalo de nuevo", text("google_error_failed"))
        assertEquals("No se pudo verificar tu cuenta de Google. Inténtalo de nuevo", text("google_error_token_invalid"))
        assertEquals(
            "Google no tiene verificado el correo de esta cuenta, así que no podemos usarla",
            text("google_error_email_not_verified"),
        )
        assertEquals(
            "Ya existe una cuenta con este correo que no está verificada. Verifícala o entra con tu contraseña antes de usar Google",
            text("google_error_local_account_not_verified"),
        )
        assertEquals("No se pudo completar el acceso con Google. Inténtalo de nuevo", text("google_error_rejected"))
        assertEquals("No se pudo completar la operación. Inténtalo de nuevo", text("google_error_unexpected"))
        assertEquals("Esta cuenta no tiene contraseña. Entra con Google", text("google_error_no_local_password"))
    }

    @Test
    fun losTextosDeGoogleNoLlevanEmojisNiJerga() {
        val googleStrings = MainSources.strings().lines().filter { it.contains("name=\"google_") }
            .joinToString("\n") { it.substringAfter(">").substringBefore("</string>") }

        assertTrue(googleStrings.isNotBlank())
        assertTrue(googleStrings.none { Character.getType(it) == Character.OTHER_SYMBOL.toInt() })
        listOf("token", "nonce", "exception", "oauth", "credential").forEach {
            assertTrue("jerga $it", !googleStrings.lowercase().contains(it))
        }
    }

    @Test
    fun todosLosErroresDeLoginTienenTexto() {
        val screen = MainSources.kotlin("ui/auth/LoginScreen.kt")

        LoginError.values().forEach {
            assertTrue("falta rama para $it", screen.contains("LoginError.${it.name} ->"))
        }
    }
}
