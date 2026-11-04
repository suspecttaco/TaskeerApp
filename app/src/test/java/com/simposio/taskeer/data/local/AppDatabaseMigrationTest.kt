package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {

    private val source: String = File(resolveLocalRoot(), "AppDatabase.kt").readText()

    @Test
    fun versionActual_esDiez() {
        assertTrue(
            "AppDatabase debe declarar la versión 10",
            Regex("version\\s*=\\s*10\\b").containsMatchIn(source),
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
    fun migraciones_incluyenLasMigraciones1a2HastaLa9a10() {
        assertTrue(
            "MIGRATIONS debe incluir de MIGRATION_1_2 a MIGRATION_9_10",
            Regex(
                "MIGRATIONS[^=]*=\\s*listOf\\(\\s*MIGRATION_1_2\\s*,\\s*MIGRATION_2_3\\s*,\\s*" +
                    "MIGRATION_3_4\\s*,\\s*MIGRATION_4_5\\s*,\\s*MIGRATION_5_6\\s*,\\s*MIGRATION_6_7\\s*,\\s*MIGRATION_7_8\\s*,\\s*MIGRATION_8_9\\s*,\\s*MIGRATION_9_10\\s*,?\\s*\\)",
            ).containsMatchIn(source),
        )
        assertTrue("las migraciones se aplican con addMigrations", source.contains("addMigrations("))
    }

    @Test
    fun migracion6a7_creaLaTablaMyDayTasks() {
        val body = migration6to7()
        assertTrue(body.contains("CREATE TABLE IF NOT EXISTS `my_day_tasks`"))
        listOf(
            "`id` TEXT NOT NULL",
            "`addedAt` INTEGER NOT NULL",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla my_day_tasks debe declarar $fragment", body.contains(fragment))
        }
    }

    @Test
    fun migracion6a7_soloCreaLaTablaYNoTocaFilas() {
        val statements = Regex("execSQL\\(").findAll(migration6to7()).count()
        val body = migration6to7().uppercase()

        assertEquals("solo debe ejecutar una sentencia", 1, statements)
        listOf("DROP", "DELETE", "ALTER", "UPDATE").forEach { keyword ->
            assertFalse(keyword, Regex("\\b$keyword\\b").containsMatchIn(body))
        }
    }

    @Test
    fun migrate7To8_asignaTareasActivasSinAsignado() {
        val body = migration7to8()

        assertTrue(body.contains("UPDATE tasks SET assigneeId = 'local-user'"))
        assertTrue(body.contains("pending = 1"))
        assertTrue(body.contains("updatedAt = "))
        assertTrue(body.contains("WHERE assigneeId IS NULL AND deletedAt IS NULL"))
    }

    @Test
    fun migrate7To8_noTocaAsignadosBorradasNiSubtareas() {
        val body = migration7to8()

        assertEquals("solo debe ejecutar una sentencia", 1, Regex("execSQL\\(").findAll(body).count())
        assertFalse("no debe tocar subtasks", body.contains("subtasks"))
        assertTrue("solo filas sin asignado", body.contains("assigneeId IS NULL"))
        assertTrue("excluye borradas", body.contains("deletedAt IS NULL"))
        listOf("DROP", "DELETE", "ALTER", "CREATE").forEach { keyword ->
            assertFalse(keyword, Regex("\\b$keyword\\b").containsMatchIn(body.uppercase()))
        }
    }

    @Test
    fun migrate7To8_conservaMarcasDeMiDia() {
        assertFalse(migration7to8().contains("my_day_tasks"))
    }

    @Test
    fun migrate8To9_creaLaTablaRemindersConLasColumnasDelModelo() {
        val body = migration8to9()

        assertTrue(body.contains("CREATE TABLE IF NOT EXISTS `reminders`"))
        listOf(
            "`id` TEXT NOT NULL",
            "`taskId` TEXT NOT NULL",
            "`remindAt` INTEGER NOT NULL",
            "`status` TEXT NOT NULL",
            "`sentAt` INTEGER, ",
            "`createdAt` INTEGER NOT NULL",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER, ",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla reminders debe declarar $fragment", body.contains(fragment))
        }
    }

    @Test
    fun migrate8To9_creaLosIndicesDelEsquemaExportado() {
        val body = migration8to9().replace(Regex("\"\\s*\\+\\s*\""), "")

        assertTrue(
            body.contains("CREATE INDEX IF NOT EXISTS `index_reminders_status_remindAt` ON `reminders` (`status`, `remindAt`)"),
        )
        assertTrue(body.contains("CREATE INDEX IF NOT EXISTS `index_reminders_taskId` ON `reminders` (`taskId`)"))
    }

    @Test
    fun migrate8To9_soloCreaYNoTocaTablasExistentes() {
        val body = migration8to9()

        assertEquals("tabla y dos índices", 3, Regex("execSQL\\(").findAll(body).count())
        listOf("DROP", "DELETE", "ALTER", "UPDATE", "INSERT").forEach { keyword ->
            assertFalse(keyword, Regex("\\b$keyword\\b").containsMatchIn(body.uppercase()))
        }
        listOf("tasks", "my_day_tasks", "boards", "columns", "subtasks").forEach { table ->
            assertFalse("no debe tocar $table", body.contains("`$table`") || body.contains(" $table "))
        }
    }

    @Test
    fun migrate9To10_creaLaTablaAppNotificationsConLasColumnasDelModelo() {
        val body = migration9to10()

        assertTrue(body.contains("CREATE TABLE IF NOT EXISTS `app_notifications`"))
        listOf(
            "`id` TEXT NOT NULL",
            "`kind` TEXT NOT NULL",
            "`taskId` TEXT, ",
            "`taskTitle` TEXT, ",
            "`activeTaskCount` INTEGER, ",
            "`delivered` INTEGER NOT NULL",
            "`createdAt` INTEGER NOT NULL",
            "`readAt` INTEGER, ",
            "`updatedAt` INTEGER NOT NULL",
            "`pending` INTEGER NOT NULL",
            "`deletedAt` INTEGER, ",
            "PRIMARY KEY(`id`)",
        ).forEach { fragment ->
            assertTrue("la tabla app_notifications debe declarar $fragment", body.contains(fragment))
        }
    }

    @Test
    fun migrate9To10_creaLosIndicesDelEsquemaExportado() {
        val body = migration9to10().replace(Regex("\"\\s*\\+\\s*\""), "")

        assertTrue(
            body.contains(
                "CREATE INDEX IF NOT EXISTS `index_app_notifications_deletedAt_createdAt` " +
                    "ON `app_notifications` (`deletedAt`, `createdAt`)",
            ),
        )
        assertTrue(
            body.contains(
                "CREATE INDEX IF NOT EXISTS `index_app_notifications_deletedAt_readAt` " +
                    "ON `app_notifications` (`deletedAt`, `readAt`)",
            ),
        )
    }

    @Test
    fun migrate9To10_soloCreaYNoTocaTablasExistentes() {
        val body = migration9to10()

        assertEquals("tabla y dos índices", 3, Regex("execSQL\\(").findAll(body).count())
        listOf("DROP", "DELETE", "ALTER", "UPDATE", "INSERT").forEach { keyword ->
            assertFalse(keyword, Regex("\\b$keyword\\b").containsMatchIn(body.uppercase()))
        }
        listOf("tasks", "my_day_tasks", "boards", "columns", "subtasks", "reminders").forEach { table ->
            assertFalse("no debe tocar $table", body.contains("`$table`") || body.contains(" $table "))
        }
    }

    private fun migration6to7(): String {
        val start = source.indexOf("Migration(6, 7)")
        assertTrue("debe declarar una Migration(6, 7)", start >= 0)
        val end = source.indexOf("private val MIGRATION_7_8", start)
        return source.substring(start, end)
    }

    private fun migration7to8(): String {
        val start = source.indexOf("Migration(7, 8)")
        assertTrue("debe declarar una Migration(7, 8)", start >= 0)
        val end = source.indexOf("private val MIGRATION_8_9", start)
        return source.substring(start, end)
    }

    private fun migration8to9(): String {
        val start = source.indexOf("Migration(8, 9)")
        assertTrue("debe declarar una Migration(8, 9)", start >= 0)
        val end = source.indexOf("private val MIGRATION_9_10", start)
        return source.substring(start, end)
    }

    private fun migration9to10(): String {
        val start = source.indexOf("Migration(9, 10)")
        assertTrue("debe declarar una Migration(9, 10)", start >= 0)
        val end = source.indexOf("private val MIGRATIONS", start)
        return source.substring(start, end)
    }

    private fun migration5to6(): String {
        val start = source.indexOf("Migration(5, 6)")
        assertTrue("debe declarar una Migration(5, 6)", start >= 0)
        val end = source.indexOf("private val MIGRATION_6_7", start)
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
