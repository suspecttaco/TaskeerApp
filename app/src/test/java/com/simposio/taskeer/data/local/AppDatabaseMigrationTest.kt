package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    private val source: String = File(resolveLocalRoot(), "AppDatabase.kt").readText()

    @Test
    fun versionActual_esTres() {
        assertTrue(
            "AppDatabase debe declarar la versión 3",
            Regex("version\\s*=\\s*3\\b").containsMatchIn(source),
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
    fun migracion2a3_creaLaTablaColumns() {
        assertTrue("debe declarar una Migration(2, 3)", source.contains("Migration(2, 3)"))
        assertTrue(
            "la migración debe crear la tabla columns",
            source.contains("CREATE TABLE IF NOT EXISTS `columns`"),
        )
        listOf(
            "`boardId` TEXT NOT NULL",
            "`name` TEXT NOT NULL",
            "`position` INTEGER NOT NULL",
            "`wipLimit` INTEGER",
            "`isDoneColumn` INTEGER NOT NULL",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla columns debe declarar $fragment", source.contains(fragment))
        }
    }

    @Test
    fun migraciones_incluyenLaMigracion1a2YLa2a3() {
        assertTrue(
            "MIGRATIONS debe incluir MIGRATION_1_2 y MIGRATION_2_3",
            Regex("MIGRATIONS[^=]*=\\s*listOf\\(\\s*MIGRATION_1_2\\s*,\\s*MIGRATION_2_3\\s*\\)")
                .containsMatchIn(source),
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
