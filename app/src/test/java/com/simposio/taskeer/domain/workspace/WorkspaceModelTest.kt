package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceModelTest {

    @Test
    fun sinDependenciasDeAndroid() {
        val files = MainSources.kotlinFiles("domain/workspace")
        val forbidden = listOf("android.", "androidx.", "com.simposio.taskeer.data", "com.simposio.taskeer.ui", "org.json")

        val violations = files.flatMap { file ->
            file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
                .map { it.removePrefix("import ") }
                .filter { imported -> forbidden.any { imported.startsWith(it) } }
                .map { "${file.name} importa $it" }
        }

        assertTrue("domain/workspace debe ser Kotlin puro: $violations", violations.isEmpty())
        assertTrue(files.isNotEmpty())
    }

    @Test
    fun toString_noIncluyeElNombre() {
        assertFalse(workspace(name = "Secreto").toString().contains("Secreto"))
    }

    @Test
    fun miembro_sinDependenciasDeAndroid() {
        val source = MainSources.kotlin("domain/workspace/WorkspaceMember.kt")
        val imports = source.lines().filter { it.startsWith("import ") }

        assertTrue(imports.none { it.startsWith("import android") || it.startsWith("import org.json") })
        assertTrue(imports.isNotEmpty())
    }

    @Test
    fun miembro_toString_noIncluyeDatosPersonales() {
        val text = member(1, name = "Secreto Nombre", email = "secreto@example.com").toString()

        assertFalse(text.contains("Secreto") || text.contains("secreto@") || text.contains(uuid(1)))
    }
}
