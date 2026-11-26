package com.simposio.taskeer.domain.workspace

import com.simposio.taskeer.testing.MainSources
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
}
