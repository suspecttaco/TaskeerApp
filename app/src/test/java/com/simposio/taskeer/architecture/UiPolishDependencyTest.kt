package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPolishDependencyTest {

    @Test
    fun iconsExtendedCatalogEntry_sinVersionPropia() {
        val line = catalogLines().firstOrNull { it.contains("\"material-icons-extended\"") }

        assertNotNull("falta material-icons-extended en el catálogo", line)
        assertTrue("debe resolverla el BOM: $line", !line!!.contains("version"))
        assertTrue(line.contains("group = \"androidx.compose.material\""))
    }

    @Test
    fun iconsExtended_esLaUnicaDependenciaNuevaDeIconos() {
        val icons = catalogLines().filter { it.contains("material-icons") }

        assertEquals(1, icons.size)
    }

    private fun catalogLines(): List<String> =
        File(findRepoRoot(), "gradle/libs.versions.toml").readLines()

    private fun findRepoRoot(): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .firstOrNull { File(it, "gradle/libs.versions.toml").isFile }
            ?: error("No encontré gradle/libs.versions.toml desde $userDir")
    }
}
