package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseSchemaTest {

    private val schema: String = resolveSchema().readText()

    @Test
    fun esquemaVersion1_estaExportado() {
        assertTrue(resolveSchema().isFile)
        assertTrue(
            "el esquema debe declarar la versión 1 de la base de datos",
            Regex("\"version\"\\s*:\\s*1\\b").containsMatchIn(schema),
        )
    }

    @Test
    fun tablaBoards_tieneLasColumnasDelConvenio() {
        assertTrue("debe declarar la tabla boards", schema.contains("\"tableName\": \"boards\""))
        listOf("id", "name", "updatedAt", "pending", "deletedAt").forEach { column(column = it) }
    }

    @Test
    fun columnaId_esTextSinAutoGenerate() {
        val id = column("id")

        assertEquals("TEXT", id.affinity)
        assertTrue("la clave primaria debe ser no nula", id.notNull)
        assertTrue("la clave primaria no debe ser autoGenerate", schema.contains("\"autoGenerate\": false"))
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

    private fun column(column: String): ColumnSchema {
        val match = Regex(
            "\\{\\s*\"fieldPath\"\\s*:\\s*\"$column\"\\s*,\\s*\"columnName\"\\s*:\\s*\"$column\"\\s*,\\s*" +
                "\"affinity\"\\s*:\\s*\"([A-Z]+)\"(?:\\s*,\\s*\"notNull\"\\s*:\\s*(true|false))?\\s*\\}",
        ).find(schema) ?: error("no encontré la columna $column en el esquema")

        return ColumnSchema(
            affinity = match.groupValues[1],
            notNull = match.groupValues.getOrNull(2)?.toBoolean() ?: false,
        )
    }

    private fun resolveSchema(): File {
        val relativePath = "app/schemas/com.simposio.taskeer.data.local.AppDatabase/1.json"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private data class ColumnSchema(val affinity: String, val notNull: Boolean)
}
