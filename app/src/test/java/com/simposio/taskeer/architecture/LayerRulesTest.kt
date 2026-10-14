package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun existenLasCapas() {
        listOf("domain", "data", "ui", "di").forEach { layer ->
            val directory = File(sourcesRoot, layer)
            assertTrue("falta la carpeta de capa $layer en ${sourcesRoot.path}", directory.isDirectory)
            assertTrue("la capa $layer no tiene fuentes Kotlin", kotlinSources(directory).isNotEmpty())
        }
    }

    @Test
    fun domainNoDependeDeCapasExteriores() {
        val forbiddenPrefixes = listOf(
            "android.",
            "androidx.",
            "com.simposio.taskeer.data",
            "com.simposio.taskeer.ui",
            "com.simposio.taskeer.di",
        )

        val violations = kotlinSources(File(sourcesRoot, "domain")).flatMap { file ->
            importsOf(file)
                .filter { imported -> forbiddenPrefixes.any { imported.startsWith(it) } }
                .map { "${file.relativeTo(sourcesRoot)} importa $it" }
        }

        assertTrue("domain/ debe ser Kotlin puro; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun uiNoInstanciaAlmacenamiento() {
        val forbiddenTokens = listOf(
            "androidx.datastore",
            "androidx.room",
            "SharedPreferences",
            "PreferencesRepositoryImpl",
        )

        val violations = kotlinSources(File(sourcesRoot, "ui")).flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("ui/ debe obtener los repositorios del contenedor; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun tiposDeRoomFueraDeDataLocal() {
        val roomTokens = listOf("androidx.room", "@Entity", "@Dao")

        val violations = kotlinSources(sourcesRoot)
            .filterNot { it.path.replace(File.separatorChar, '/').contains("data/local/") }
            .flatMap { file ->
                roomTokens
                    .filter { file.readText().contains(it) }
                    .map { "${file.relativeTo(sourcesRoot)} contiene $it" }
            }

        assertTrue("los tipos de Room solo pueden vivir en data/local/; violaciones: $violations", violations.isEmpty())
    }

    private fun importsOf(file: File): List<String> = file.readLines()
        .map { it.trim() }
        .filter { it.startsWith("import ") }
        .map { it.removePrefix("import ").removeSuffix(";").trim() }

    private fun kotlinSources(directory: File): List<File> =
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
