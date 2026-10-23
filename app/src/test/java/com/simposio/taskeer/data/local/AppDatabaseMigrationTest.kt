package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    private val source: String = File(resolveLocalRoot(), "AppDatabase.kt").readText()

    @Test
    fun versionActual_esCuatro() {
        assertTrue(
            "AppDatabase debe declarar la versión 4",
            Regex("version\\s*=\\s*4\\b").containsMatchIn(source),
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
    fun migracion3a4_creaLaTablaTasks() {
        assertTrue("debe declarar una Migration(3, 4)", source.contains("Migration(3, 4)"))
        assertTrue(
            "la migración debe crear la tabla tasks",
            source.contains("CREATE TABLE IF NOT EXISTS `tasks`"),
        )
        listOf(
            "`id` TEXT NOT NULL",
            "`columnId` TEXT NOT NULL",
            "`title` TEXT NOT NULL",
            "`description` TEXT",
            "`priority` TEXT NOT NULL",
            "`dueAt` INTEGER",
            "`assigneeId` TEXT",
            "`position` INTEGER NOT NULL",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla tasks debe declarar $fragment", source.contains(fragment))
        }
        assertTrue(
            "la migración debe crear el índice de columna y posición",
            source.contains("CREATE INDEX IF NOT EXISTS `index_tasks_columnId_position`"),
        )
        assertTrue(
            "el índice debe cubrir columnId y position",
            source.contains("ON `tasks` (`columnId`, `position`)"),
        )
    }

    @Test
    fun migraciones_incluyenLaMigracion1a2La2a3YLa3a4() {
        assertTrue(
            "MIGRATIONS debe incluir MIGRATION_1_2, MIGRATION_2_3 y MIGRATION_3_4",
            Regex(
                "MIGRATIONS[^=]*=\\s*listOf\\(\\s*MIGRATION_1_2\\s*,\\s*MIGRATION_2_3\\s*,\\s*MIGRATION_3_4\\s*\\)",
            ).containsMatchIn(source),
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
