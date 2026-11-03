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
            val query = querySource(name)
            assertTrue("$name: columnas de completadas", query.contains("c.isDoneColumn = 0"))
            assertTrue("$name: tarea borrada", query.contains("t.deletedAt IS NULL"))
            assertTrue("$name: columna borrada", query.contains("c.deletedAt IS NULL"))
            assertTrue("$name: tablero borrado", query.contains("b.deletedAt IS NULL"))
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
