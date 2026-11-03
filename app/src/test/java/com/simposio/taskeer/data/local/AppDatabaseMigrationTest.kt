package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    private val source: String = File(resolveLocalRoot(), "AppDatabase.kt").readText()

    @Test
    fun versionActual_esSeis() {
        assertTrue(
            "AppDatabase debe declarar la versión 6",
            Regex("version\\s*=\\s*6\\b").containsMatchIn(source),
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
    fun migracion4a5_creaLaTablaSubtasks() {
        assertTrue("debe declarar una Migration(4, 5)", source.contains("Migration(4, 5)"))
        assertTrue(
            "la migración debe crear la tabla subtasks",
            source.contains("CREATE TABLE IF NOT EXISTS `subtasks`"),
        )
        listOf(
            "`id` TEXT NOT NULL",
            "`taskId` TEXT NOT NULL",
            "`title` TEXT NOT NULL",
            "`completed` INTEGER NOT NULL",
            "`position` INTEGER NOT NULL",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla subtasks debe declarar $fragment", source.contains(fragment))
        }
        assertTrue(
            "la migración debe crear el índice de tarea y posición",
            source.contains("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId_position`"),
        )
        assertTrue(
            "el índice debe cubrir taskId y position",
            source.contains("ON `subtasks` (`taskId`, `position`)"),
        )
    }

    @Test
    fun migracion5a6_anadeLasCincoColumnas() {
        val body = migration5to6()
        listOf(
            "ALTER TABLE subtasks ADD COLUMN assigneeId TEXT\"",
            "ALTER TABLE subtasks ADD COLUMN dueAt INTEGER\"",
            "ALTER TABLE columns ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE subtasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0",
        ).forEach { statement ->
            assertTrue("la migración 5 a 6 debe contener $statement", body.contains(statement))
        }
    }

    @Test
    fun migracion5a6_rellenaCreatedAtConUpdatedAtDespuesDeAnadirLaColumna() {
        val body = migration5to6()
        listOf("columns", "tasks", "subtasks").forEach { table ->
            val add = body.indexOf("ALTER TABLE $table ADD COLUMN createdAt")
            val update = body.indexOf("UPDATE $table SET createdAt = updatedAt\"")
            assertTrue("falta ADD COLUMN createdAt en $table", add >= 0)
            assertTrue("falta el relleno de createdAt en $table", update > add)
        }
    }

    @Test
    fun migracion5a6_noEsDestructiva() {
        val body = migration5to6().uppercase()

        assertFalse("no debe borrar tablas", body.contains("DROP"))
        assertFalse("no debe borrar filas", body.contains("DELETE"))
    }

    @Test
    fun migraciones_incluyenLasMigraciones1a2HastaLa5a6() {
        assertTrue(
            "MIGRATIONS debe incluir de MIGRATION_1_2 a MIGRATION_5_6",
            Regex(
                "MIGRATIONS[^=]*=\\s*listOf\\(\\s*MIGRATION_1_2\\s*,\\s*MIGRATION_2_3\\s*,\\s*" +
                    "MIGRATION_3_4\\s*,\\s*MIGRATION_4_5\\s*,\\s*MIGRATION_5_6\\s*\\)",
            ).containsMatchIn(source),
        )
        assertTrue("las migraciones se aplican con addMigrations", source.contains("addMigrations("))
    }

    private fun migration5to6(): String {
        val start = source.indexOf("Migration(5, 6)")
        assertTrue("debe declarar una Migration(5, 6)", start >= 0)
        val end = source.indexOf("private val MIGRATIONS", start)
        return source.substring(start, end)
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
