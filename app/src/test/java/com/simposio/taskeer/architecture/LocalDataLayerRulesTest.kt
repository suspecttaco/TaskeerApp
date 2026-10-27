package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataLayerRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun dataLocalSinBorradoFisico() {
        val violations = dataLocalSources().flatMap { file ->
            listOf("@Delete", "DELETE FROM")
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} contiene $it" }
        }

        assertTrue("el borrado siempre es lógico; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun appDatabaseDeclaraMigracionesExplicitas() {
        val source = File(sourcesRoot, "data/local/AppDatabase.kt").readText()

        assertTrue("AppDatabase debe declarar MIGRATIONS", source.contains("MIGRATIONS"))
        assertTrue("AppDatabase debe aplicar las migraciones con addMigrations", source.contains("addMigrations("))
        assertTrue("AppDatabase debe exportar el esquema", source.contains("exportSchema = true"))
        assertFalse("no se permite borrado destructivo", source.contains("fallbackToDestructive"))
    }

    @Test
    fun todaEntidadImplementaLaConvencion() {
        val entitySources = dataLocalSources().filter { it.readText().contains("@Entity") }

        assertTrue("debe existir al menos una entidad en data/local/", entitySources.isNotEmpty())
        val violations = entitySources
            .filterNot { it.readText().contains("SyncableEntity") }
            .map { "${it.relativeTo(sourcesRoot)} no implementa SyncableEntity" }

        assertTrue("toda entidad debe implementar la convención; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun domainYUiSinTiposDeDataLocal() {
        val forbiddenTokens = listOf(
            "androidx.room",
            "AppDatabase",
            "BoardEntity",
            "BoardDao",
            "BoardMapping",
            "ColumnEntity",
            "ColumnDao",
            "ColumnMapping",
            "TaskEntity",
            "TaskDao",
            "TaskMapping",
            "SubtaskEntity",
            "SubtaskDao",
            "SubtaskMapping",
            "SyncableEntity",
        )

        val violations = listOf("domain", "ui").flatMap { layer ->
            kotlinSources(File(sourcesRoot, layer)).flatMap { file ->
                forbiddenTokens
                    .filter { file.readText().contains(it) }
                    .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
            }
        }

        assertTrue("domain/ y ui/ no deben conocer la capa local; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun contratosDeRepositorioNeutros() {
        val forbiddenTokens = listOf("androidx.room", "data/local/", "data/remote/", "androidx.datastore")
        val contracts = File(sourcesRoot, "data/repository")
            .listFiles { file -> file.isFile && file.name.endsWith("Repository.kt") && !file.name.endsWith("Impl.kt") }
            ?.toList()
            .orEmpty()

        assertTrue("debe existir al menos un contrato de repositorio", contracts.isNotEmpty())
        val violations = contracts.flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("los contratos deben ser neutros; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun uiSinImplementacionesConcretas() {
        val forbiddenTokens = listOf(
            "androidx.room",
            "androidx.datastore",
            "RepositoryImpl",
            "AppDatabase",
            "BoardDao",
            "BoardEntity",
            "BoardMapping",
            "ColumnDao",
            "ColumnEntity",
            "ColumnMapping",
            "TaskDao",
            "TaskEntity",
            "TaskMapping",
            "SubtaskEntity",
            "SubtaskDao",
            "SubtaskMapping",
            "SyncableEntity",
        )

        val violations = kotlinSources(File(sourcesRoot, "ui")).flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("ui/ no debe tocar almacén ni implementaciones; violaciones: $violations", violations.isEmpty())
    }

    private fun dataLocalSources(): List<File> = kotlinSources(File(sourcesRoot, "data/local"))

    private fun kotlinSources(directory: File): List<File> =
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
