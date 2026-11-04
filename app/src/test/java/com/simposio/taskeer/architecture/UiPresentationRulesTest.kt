package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPresentationRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun iconoDirecto_soloEnComponents() {
        val direct = Regex("(?<![A-Za-z])Icon\\(")
        val violations = uiFiles()
            .filterNot { it.relativeTo(sourcesRoot).path.startsWith("ui/components") }
            .filter { direct.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("Icon( solo en ui/components: $violations", violations.isEmpty())
    }

    @Test
    fun sinGlifosDeTextoComoIcono() {
        val violations = uiFiles()
            .filter { file -> GLYPHS.any { file.readText().contains(it) } }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("glifos de texto como icono: $violations", violations.isEmpty())
    }

    @Test
    fun componentsNoImportaDataNiRoom() {
        val forbidden = listOf("com.simposio.taskeer.data", "com.simposio.taskeer.di", "androidx.room")
        val files = File(sourcesRoot, "ui/components").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue("deben existir los componentes compartidos", files.isNotEmpty())
        val violations = files.flatMap { file ->
            forbidden.filter { file.readText().contains(it) }
                .map { "${file.name} referencia $it" }
        }
        assertTrue("components solo depende de compose, R y theme: $violations", violations.isEmpty())
    }

    @Test
    fun actionIconExigeDescripcionNoNula() {
        val source = File(sourcesRoot, "ui/components/ActionIcon.kt").readText()

        assertTrue(source.contains("contentDescription: String,"))
        assertTrue(!source.contains("contentDescription: String?"))
    }

    @Test
    fun sinDpLiteralesEnPantallas() {
        val literal = Regex("(padding|spacedBy|size)\\([^)]*\\d\\.dp")
        val violations = uiFiles()
            .filter { file ->
                val relative = file.relativeTo(sourcesRoot).path
                !relative.startsWith("ui/components") && !relative.startsWith("ui/theme")
            }
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.relativeTo(sourcesRoot).path }

        assertTrue("dp literales de espaciado en: $violations", violations.isEmpty())
    }

    @Test
    fun editorUsaDesplegableParaPrioridad() {
        val source = File(sourcesRoot, "ui/tasks/TaskEditorDialog.kt").readText()

        assertTrue(source.contains("options = TaskPriority.entries"))
        assertTrue(!Regex("RadioOption\\([^)]*priority", RegexOption.DOT_MATCHES_ALL).containsMatchIn(source))
    }

    private fun uiFiles(): List<File> = File(sourcesRoot, "ui").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private companion object {
        val GLYPHS = listOf("★", "☆", "✓")
    }
}
