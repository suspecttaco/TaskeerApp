package com.simposio.taskeer.data.local

import java.io.File
import java.lang.reflect.Method
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyDayDaoConventionsTest {

    private val source: String = File(resolveLocalRoot(), "MyDayDao.kt").readText()

    @Test
    fun consultasReactivas_devuelvenFlow() {
        listOf("observeMarkedIds", "observePinned", "observeAssigned", "observeOverdueCount").forEach {
            assertEquals(it, Flow::class.java, method(it).returnType)
        }
    }

    @Test
    fun observeOverdueCount_esCountSinLimite() {
        val query = querySource("observeOverdueCount")

        assertTrue(query.contains("SELECT COUNT(*)"))
        assertTrue(query.contains("t.dueAt < :now"))
        assertFalse(query.uppercase().contains("LIMIT"))
        assertFalse(source.uppercase().contains("LIMIT"))
    }

    @Test
    fun bloquesYConteo_excluyenColumnasDeCompletadasYBorrados() {
        listOf("observePinned", "observeAssigned", "observeOverdueCount").forEach { name ->
            assertTrue("$name: condición de visibilidad compartida", querySource(name).contains("VISIBLE"))
        }
        val visible = constantSource("VISIBLE")
        assertTrue("columnas de completadas", visible.contains("c.isDoneColumn = 0"))
        assertTrue("tarea borrada", visible.contains("t.deletedAt IS NULL"))
        assertTrue("columna borrada", visible.contains("c.deletedAt IS NULL"))
        assertTrue("tablero borrado", visible.contains("b.deletedAt IS NULL"))
    }

    @Test
    fun consultas_usanLeftJoinYRamaDeBandeja() {
        val from = constantSource("FROM_TASKS")

        assertTrue(from.contains("LEFT JOIN columns c ON t.columnId = c.id"))
        assertTrue(from.contains("LEFT JOIN boards b ON c.boardId = b.id"))
        assertTrue(constantSource("VISIBLE").contains("t.columnId IS NULL OR"))
        assertTrue(querySource("observePinned").contains("LEFT JOIN columns c ON t.columnId = c.id"))
        assertTrue(querySource("observeAssigned").contains("FROM_TASKS"))
        assertTrue(querySource("observeOverdueCount").contains("FROM_TASKS"))
    }

    @Test
    fun consultas_exigenColumnaYTableroExistentesParaTareasConColumna() {
        assertTrue(constantSource("VISIBLE").contains("c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL"))
    }

    @Test
    fun consultas_comparteLaCondicionDeVisibilidad() {
        listOf("observePinned", "observeAssigned", "observeOverdueCount").forEach { name ->
            assertFalse("$name no duplica la condición", querySource(name).contains("c.isDoneColumn"))
        }
    }

    @Test
    fun ordenes_sonEstablesConDesempatePorId() {
        assertTrue(querySource("observePinned").contains("ORDER BY m.addedAt DESC, t.id ASC"))
        assertTrue(querySource("observeAssigned").contains("ORDER BY t.dueAt ASC, t.id ASC"))
    }

    @Test
    fun myDayDao_noBorraFisicamente() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
        assertTrue(source.contains("OnConflictStrategy.REPLACE"))
    }

    private fun constantSource(name: String): String {
        val start = source.indexOf("private const val $name")
        assertTrue("falta la constante $name", start >= 0)
        return source.substring(start, source.indexOf("\n\n", start).takeIf { it > 0 } ?: source.length)
    }

    private fun querySource(name: String): String {
        val start = source.indexOf("abstract fun $name(")
        assertTrue("falta $name", start >= 0)
        val annotation = source.lastIndexOf("@Query(", start)
        return source.substring(annotation, start)
    }

    private fun method(name: String): Method =
        MyDayDao::class.java.methods.singleOrNull { it.name == name }
            ?: error("MyDayDao no declara $name")

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}
