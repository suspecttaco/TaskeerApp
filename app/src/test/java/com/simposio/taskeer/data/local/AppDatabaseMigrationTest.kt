package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    private val source: String = File(resolveLocalRoot(), "AppDatabase.kt").readText()

    @Test
    fun versionActual_esDos() {
        assertTrue(
            "AppDatabase debe declarar la versión 2",
            Regex("version\\s*=\\s*2\\b").containsMatchIn(source),
        )
    }

    @Test
    fun migracion1a2_anadeCreatedAtNoNulo() {
        assertTrue("debe declarar una Migration(1, 2)", source.contains("Migration(1, 2)"))
        assertTrue(
            "la migración debe añadir createdAt como INTEGER NOT NULL",
            source.contains("ALTER TABLE boards ADD COLUMN createdAt INTEGER NOT NULL"),
        )
    }

    @Test
    fun migraciones_incluyenLaMigracion1a2() {
        assertTrue(
            "MIGRATIONS debe incluir MIGRATION_1_2",
            Regex("MIGRATIONS[^=]*=\\s*listOf\\(\\s*MIGRATION_1_2\\s*\\)").containsMatchIn(source),
        )
        assertTrue("las migraciones se aplican con addMigrations", source.contains("addMigrations("))
    }

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
