package com.simposio.taskeer.ui

import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ErrorPageKind
import com.simposio.taskeer.ui.components.errorPageSpecFor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPageSpecTest {

    private val specs = ErrorPageKind.entries.map { it to errorPageSpecFor(it) }

    @Test
    fun hayUnaPaginaPorCadaTipo() {
        assertEquals(6, ErrorPageKind.entries.size)
    }

    @Test
    fun cadaTipoTieneTituloMensajeYAccionPropios() {
        specs.forEach { (kind, spec) ->
            assertTrue("$kind sin título", spec.titleRes != 0)
            assertTrue("$kind sin mensaje", spec.messageRes != 0)
            assertTrue("$kind sin acción", spec.actionRes != 0)
        }
        assertEquals(R.string.error_not_found_title, errorPageSpecFor(ErrorPageKind.NOT_FOUND).titleRes)
        assertEquals(R.string.error_offline_message, errorPageSpecFor(ErrorPageKind.OFFLINE).messageRes)
    }

    @Test
    fun lasEtiquetasDeAccionSonLasEsperadas() {
        val actions = specs.associate { (kind, spec) -> kind to value(spec.actionRes) }

        assertEquals("Ir al inicio", actions[ErrorPageKind.NOT_FOUND])
        assertEquals("Ir al inicio", actions[ErrorPageKind.NO_PERMISSION])
        assertEquals("Reintentar", actions[ErrorPageKind.UNEXPECTED])
        assertEquals("Reintentar", actions[ErrorPageKind.SERVICE_UNAVAILABLE])
        assertEquals("Reintentar", actions[ErrorPageKind.OFFLINE])
        assertEquals("Iniciar sesión", actions[ErrorPageKind.SESSION_EXPIRED])
    }

    @Test
    fun titulosYMensajesSonUnicos() {
        assertEquals(6, specs.map { it.second.titleRes }.toSet().size)
        assertEquals(6, specs.map { it.second.messageRes }.toSet().size)
        assertEquals(6, specs.map { value(it.second.titleRes) }.toSet().size)
    }

    private fun value(id: Int): String {
        val name = R.string::class.java.fields.first { it.getInt(null) == id }.name
        val strings = resolveStrings().readText()
        return requireNotNull(Regex("""name="$name">(.*?)</string>""").find(strings)).groupValues[1]
    }

    private fun resolveStrings(): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, "app/src/main/res/values/strings.xml") }
            .first { it.isFile }
    }
}
