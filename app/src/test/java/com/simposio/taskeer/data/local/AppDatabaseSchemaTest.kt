package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseSchemaTest {

    private val schemaV2: String = resolveSchema(version = 2).readText()
    private val schemaV3: String = resolveSchema(version = 3).readText()
    private val schemaV4: String = resolveSchema(version = 4).readText()
    private val schemaV5: String = resolveSchema(version = 5).readText()
    private val schemaV6: String = resolveSchema(version = 6).readText()
    private val schemaV8: String = resolveSchema(version = 8).readText()
    private val schemaV9: String = resolveSchema(version = 9).readText()
    private val schemaV10: String = resolveSchema(version = 10).readText()

    @Test
    fun esquemaVersion1_sigueExportado() {
        val schemaV1 = resolveSchema(version = 1)

        assertTrue(schemaV1.isFile)
        assertTrue(
            "el esquema conservado debe declarar la versión 1",
            Regex("\"version\"\\s*:\\s*1\\b").containsMatchIn(schemaV1.readText()),
        )
    }

    @Test
    fun esquemaVersion2_sigueExportado() {
        assertTrue(resolveSchema(version = 2).isFile)
        assertTrue(
            "el esquema debe declarar la versión 2 de la base de datos",
            Regex("\"version\"\\s*:\\s*2\\b").containsMatchIn(schemaV2),
        )
    }

    @Test
    fun esquemaVersion3_sigueExportado() {
        assertTrue(resolveSchema(version = 3).isFile)
        assertTrue(
            "el esquema debe declarar la versión 3 de la base de datos",
            Regex("\"version\"\\s*:\\s*3\\b").containsMatchIn(schemaV3),
        )
    }

    @Test
    fun esquemaVersion4_estaExportado() {
        assertTrue(resolveSchema(version = 4).isFile)
        assertTrue(
            "el esquema debe declarar la versión 4 de la base de datos",
            Regex("\"version\"\\s*:\\s*4\\b").containsMatchIn(schemaV4),
        )
    }

    @Test
    fun esquemaVersion5_estaExportado() {
        assertTrue(resolveSchema(version = 5).isFile)
        assertTrue(
            "el esquema debe declarar la versión 5 de la base de datos",
            Regex("\"version\"\\s*:\\s*5\\b").containsMatchIn(schemaV5),
        )
    }

    @Test
    fun tablaBoards_tieneLasColumnasDelConvenio() {
        assertTrue("debe declarar la tabla boards", schemaV2.contains("\"tableName\": \"boards\""))
        listOf("id", "name", "createdAt", "updatedAt", "pending", "deletedAt").forEach { column(column = it) }
    }

    @Test
    fun columnaCreatedAt_esIntegerNoNula() {
        val createdAt = column("createdAt")

        assertEquals("INTEGER", createdAt.affinity)
        assertTrue("createdAt debe ser no nula", createdAt.notNull)
    }

    @Test
    fun columnaId_esTextSinAutoGenerate() {
        val id = column("id")

        assertEquals("TEXT", id.affinity)
        assertTrue("la clave primaria debe ser no nula", id.notNull)
        assertTrue("la clave primaria no debe ser autoGenerate", schemaV2.contains("\"autoGenerate\": false"))
    }

    @Test
    fun columnaDeletedAt_esNula() {
        val deletedAt = column("deletedAt")
        val updatedAt = column("updatedAt")
        val pending = column("pending")

        assertEquals("INTEGER", deletedAt.affinity)
        assertFalse("deletedAt debe admitir NULL", deletedAt.notNull)
        assertEquals("INTEGER", updatedAt.affinity)
        assertTrue("updatedAt debe ser no nula", updatedAt.notNull)
        assertEquals("INTEGER", pending.affinity)
        assertTrue("pending debe ser no nula", pending.notNull)
    }

    @Test
    fun tablaColumns_tieneLasColumnasDelModelo() {
        val columnsTable = columnsTable()

        listOf("id", "boardId", "name", "position", "wipLimit", "isDoneColumn", "updatedAt", "pending", "deletedAt")
            .forEach { column(columnsTable, it) }
    }

    @Test
    fun columnaBoardId_esTextNoNula() {
        val boardId = column(columnsTable(), "boardId")

        assertEquals("TEXT", boardId.affinity)
        assertTrue("boardId debe ser no nula", boardId.notNull)
    }

    @Test
    fun columnaPosition_esIntegerNoNula() {
        val position = column(columnsTable(), "position")

        assertEquals("INTEGER", position.affinity)
        assertTrue("position debe ser no nula", position.notNull)
    }

    @Test
    fun columnaWipLimit_admiteNull() {
        val wipLimit = column(columnsTable(), "wipLimit")

        assertEquals("INTEGER", wipLimit.affinity)
        assertFalse("wipLimit debe admitir NULL", wipLimit.notNull)
    }

    @Test
    fun tablaTasks_tieneLasColumnasDelModelo() {
        val tasksTable = tasksTable()

        listOf(
            "id",
            "columnId",
            "title",
            "description",
            "priority",
            "dueAt",
            "assigneeId",
            "position",
            "updatedAt",
            "pending",
            "deletedAt",
        ).forEach { column(tasksTable, it) }
    }

    @Test
    fun columnaPriority_esTextNoNula() {
        val priority = column(tasksTable(), "priority")

        assertEquals("TEXT", priority.affinity)
        assertTrue("priority debe ser no nula", priority.notNull)
    }

    @Test
    fun columnaAssigneeId_admiteNull() {
        val assigneeId = column(tasksTable(), "assigneeId")

        assertEquals("TEXT", assigneeId.affinity)
        assertFalse("assigneeId debe admitir NULL", assigneeId.notNull)
    }

    @Test
    fun columnaDueAt_admiteNull() {
        val dueAt = column(tasksTable(), "dueAt")

        assertEquals("INTEGER", dueAt.affinity)
        assertFalse("dueAt debe admitir NULL", dueAt.notNull)
    }

    @Test
    fun indiceTasksColumnIdPosition_estaExportado() {
        assertTrue(
            "el esquema v4 debe exportar el índice de columna y posición",
            schemaV4.contains("\"name\": \"index_tasks_columnId_position\""),
        )
        val index = schemaV4.substring(schemaV4.indexOf("\"name\": \"index_tasks_columnId_position\""))
        assertTrue(
            "el índice debe cubrir columnId y position",
            index.contains("`columnId`, `position`"),
        )
    }

    @Test
    fun tablaSubtasks_tieneLasColumnasDelModelo() {
        val subtasksTable = subtasksTable()

        listOf("id", "taskId", "title", "completed", "position", "updatedAt", "pending", "deletedAt")
            .forEach { column(subtasksTable, it) }
    }

    @Test
    fun columnaCompleted_esIntegerNoNula() {
        val completed = column(subtasksTable(), "completed")

        assertEquals("INTEGER", completed.affinity)
        assertTrue("completed debe ser no nula", completed.notNull)
    }

    @Test
    fun indiceSubtasksTaskIdPosition_estaExportado() {
        val marker = "\"name\": \"index_subtasks_taskId_position\""
        assertTrue("el esquema v5 debe exportar el índice de tarea y posición", schemaV5.contains(marker))
        assertTrue(
            "el índice debe cubrir taskId y position",
            schemaV5.substring(schemaV5.indexOf(marker)).contains("`taskId`, `position`"),
        )
    }

    @Test
    fun esquemaVersion6_estaExportadoYConservaDel1Al5() {
        assertTrue(resolveSchema(version = 6).isFile)
        assertTrue(
            "el esquema debe declarar la versión 6 de la base de datos",
            Regex("\"version\"\\s*:\\s*6\\b").containsMatchIn(schemaV6),
        )
        (1..5).forEach { assertTrue(resolveSchema(version = it).isFile) }
    }

    @Test
    fun v6_subtasksAssigneeIdYDueAt_sonNulas() {
        val subtasks = tableV6("subtasks")

        assertEquals(ColumnSchema("TEXT", false), column(subtasks, "assigneeId"))
        assertEquals(ColumnSchema("INTEGER", false), column(subtasks, "dueAt"))
    }

    @Test
    fun v6_createdAtEsIntegerNoNuloEnColumnsTasksYSubtasks() {
        listOf("columns", "tasks", "subtasks").forEach { table ->
            assertEquals(
                "createdAt de $table",
                ColumnSchema("INTEGER", true),
                column(tableV6(table), "createdAt"),
            )
        }
    }

    @Test
    fun esquemaVersion8_estaExportadoYConservaDel1Al7() {
        assertTrue(
            "el esquema debe declarar la versión 8 de la base de datos",
            Regex("\"version\"\\s*:\\s*8\\b").containsMatchIn(schemaV8),
        )
        (1..7).forEach { assertTrue(resolveSchema(version = it).isFile) }
    }

    @Test
    fun v8_myDayTasks_tieneLasColumnasDelModelo() {
        val table = tableV8("my_day_tasks")

        assertEquals(ColumnSchema("TEXT", true), column(table, "id"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "addedAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "updatedAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "pending"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "deletedAt"))
    }

    @Test
    fun v8_myDayTasks_usaElIdDeLaTareaComoClavePrimaria() {
        val table = tableV8("my_day_tasks")

        assertTrue(table.contains("\"autoGenerate\": false"))
        assertTrue(Regex("\"columnNames\"\\s*:\\s*\\[\\s*\"id\"\\s*\\]").containsMatchIn(table))
    }

    @Test
    fun v8_conservaLasCuatroTablasAnteriores() {
        listOf("boards", "columns", "tasks", "subtasks").forEach { tableV8(it) }
    }

    @Test
    fun esquemaVersion9_estaExportadoYConservaDel1Al8() {
        assertTrue(
            "el esquema debe declarar la versión 9 de la base de datos",
            Regex("\"version\"\\s*:\\s*9\\b").containsMatchIn(schemaV9),
        )
        (1..8).forEach { assertTrue(resolveSchema(version = it).isFile) }
    }

    @Test
    fun v9_reminders_tieneLasColumnasDelModelo() {
        val table = tableV9("reminders")

        assertEquals(ColumnSchema("TEXT", true), column(table, "id"))
        assertEquals(ColumnSchema("TEXT", true), column(table, "taskId"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "remindAt"))
        assertEquals(ColumnSchema("TEXT", true), column(table, "status"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "sentAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "createdAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "updatedAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "pending"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "deletedAt"))
        assertTrue(table.contains("\"autoGenerate\": false"))
    }

    @Test
    fun v9_reminders_declaraLosIndicesDeConsulta() {
        val table = tableV9("reminders")

        assertTrue(table.contains("index_reminders_status_remindAt"))
        assertTrue(table.contains("index_reminders_taskId"))
    }

    @Test
    fun v9_conservaLasCincoTablasAnteriores() {
        listOf("boards", "columns", "tasks", "subtasks", "my_day_tasks").forEach { tableV9(it) }
    }

    @Test
    fun esquemaVersion10_estaExportadoYConservaDel1Al9() {
        assertTrue(
            "el esquema debe declarar la versión 10 de la base de datos",
            Regex("\"version\"\\s*:\\s*10\\b").containsMatchIn(schemaV10),
        )
        (1..9).forEach { assertTrue(resolveSchema(version = it).isFile) }
    }

    @Test
    fun v10_appNotifications_tieneLasColumnasDelModelo() {
        val table = tableV10("app_notifications")

        assertEquals(ColumnSchema("TEXT", true), column(table, "id"))
        assertEquals(ColumnSchema("TEXT", true), column(table, "kind"))
        assertEquals(ColumnSchema("TEXT", false), column(table, "taskId"))
        assertEquals(ColumnSchema("TEXT", false), column(table, "taskTitle"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "activeTaskCount"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "delivered"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "createdAt"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "readAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "updatedAt"))
        assertEquals(ColumnSchema("INTEGER", true), column(table, "pending"))
        assertEquals(ColumnSchema("INTEGER", false), column(table, "deletedAt"))
        assertTrue(table.contains("\"autoGenerate\": false"))
    }

    @Test
    fun v10_appNotifications_declaraLosIndicesDeConsulta() {
        val table = tableV10("app_notifications")

        assertTrue(table.contains("index_app_notifications_deletedAt_createdAt"))
        assertTrue(table.contains("index_app_notifications_deletedAt_readAt"))
    }

    @Test
    fun v10_conservaLasSeisTablasAnteriores() {
        listOf("boards", "columns", "tasks", "subtasks", "my_day_tasks", "reminders").forEach { tableV10(it) }
    }

    @Test
    fun v10_soloAnadeLaTablaAppNotificationsRespectoAv9() {
        val tablesV9 = Regex("\"tableName\": \"(\\w+)\"").findAll(schemaV9).map { it.groupValues[1] }.toSet()
        val tablesV10 = Regex("\"tableName\": \"(\\w+)\"").findAll(schemaV10).map { it.groupValues[1] }.toSet()

        assertEquals(setOf("app_notifications"), tablesV10 - tablesV9)
        assertTrue(tablesV9.all { it in tablesV10 })
    }

    private fun tableV10(name: String): String {
        val start = schemaV10.indexOf("\"tableName\": \"$name\"")
        assertTrue("el esquema v10 debe declarar la tabla $name", start >= 0)
        val nextTable = schemaV10.indexOf("\"tableName\":", start + 1)
        return schemaV10.substring(start, if (nextTable < 0) schemaV10.length else nextTable)
    }

    private fun tableV9(name: String): String {
        val start = schemaV9.indexOf("\"tableName\": \"$name\"")
        assertTrue("el esquema v9 debe declarar la tabla $name", start >= 0)
        val nextTable = schemaV9.indexOf("\"tableName\":", start + 1)
        return schemaV9.substring(start, if (nextTable < 0) schemaV9.length else nextTable)
    }

    private fun tableV8(name: String): String {
        val start = schemaV8.indexOf("\"tableName\": \"$name\"")
        assertTrue("el esquema v8 debe declarar la tabla $name", start >= 0)
        val nextTable = schemaV8.indexOf("\"tableName\":", start + 1)
        return schemaV8.substring(start, if (nextTable < 0) schemaV8.length else nextTable)
    }

    private fun tableV6(name: String): String {
        val start = schemaV6.indexOf("\"tableName\": \"$name\"")
        assertTrue("el esquema v6 debe declarar la tabla $name", start >= 0)
        val nextTable = schemaV6.indexOf("\"tableName\":", start + 1)
        return schemaV6.substring(start, if (nextTable < 0) schemaV6.length else nextTable)
    }

    private fun subtasksTable(): String {
        val start = schemaV5.indexOf("\"tableName\": \"subtasks\"")
        assertTrue("el esquema v5 debe declarar la tabla subtasks", start >= 0)
        val nextTable = schemaV5.indexOf("\"tableName\":", start + 1)
        return schemaV5.substring(start, if (nextTable < 0) schemaV5.length else nextTable)
    }

    private fun tasksTable(): String {
        val start = schemaV4.indexOf("\"tableName\": \"tasks\"")
        assertTrue("el esquema v4 debe declarar la tabla tasks", start >= 0)
        val nextTable = schemaV4.indexOf("\"tableName\":", start + 1)
        return schemaV4.substring(start, if (nextTable < 0) schemaV4.length else nextTable)
    }

    private fun columnsTable(): String {
        val start = schemaV3.indexOf("\"tableName\": \"columns\"")
        assertTrue("el esquema v3 debe declarar la tabla columns", start >= 0)
        val nextTable = schemaV3.indexOf("\"tableName\":", start + 1)
        return schemaV3.substring(start, if (nextTable < 0) schemaV3.length else nextTable)
    }

    private fun column(column: String): ColumnSchema = column(schemaV2, column)

    private fun column(schema: String, column: String): ColumnSchema {
        val match = Regex(
            "\\{\\s*\"fieldPath\"\\s*:\\s*\"$column\"\\s*,\\s*\"columnName\"\\s*:\\s*\"$column\"\\s*,\\s*" +
                "\"affinity\"\\s*:\\s*\"([A-Z]+)\"(?:\\s*,\\s*\"notNull\"\\s*:\\s*(true|false))?\\s*\\}",
        ).find(schema) ?: error("no encontré la columna $column en el esquema")

        return ColumnSchema(
            affinity = match.groupValues[1],
            notNull = match.groupValues.getOrNull(2)?.toBoolean() ?: false,
        )
    }

    private fun resolveSchema(version: Int): File {
        val relativePath = "app/schemas/com.simposio.taskeer.data.local.AppDatabase/$version.json"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private data class ColumnSchema(val affinity: String, val notNull: Boolean)
}
