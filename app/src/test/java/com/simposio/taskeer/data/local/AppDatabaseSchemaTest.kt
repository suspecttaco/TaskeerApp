package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseSchemaTest {

    private val schemaV2: String = resolveSchema(version = 2).readText()

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
    fun esquemaVersion2_estaExportado() {
        assertTrue(resolveSchema(version = 2).isFile)
        assertTrue(
            "el esquema debe declarar la versión 2 de la base de datos",
            Regex("\"version\"\\s*:\\s*2\\b").containsMatchIn(schemaV2),
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

    private fun column(column: String): ColumnSchema {
        val match = Regex(
            "\\{\\s*\"fieldPath\"\\s*:\\s*\"$column\"\\s*,\\s*\"columnName\"\\s*:\\s*\"$column\"\\s*,\\s*" +
                "\"affinity\"\\s*:\\s*\"([A-Z]+)\"(?:\\s*,\\s*\"notNull\"\\s*:\\s*(true|false))?\\s*\\}",
        ).find(schemaV2) ?: error("no encontré la columna $column en el esquema")

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
