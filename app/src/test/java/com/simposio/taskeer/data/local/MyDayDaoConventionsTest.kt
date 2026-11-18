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
        listOf(
            "observeMarkedIds", "observePinned", "observeAssigned", "observeSuggestions",
            "observeDated", "observeOverdueCount",
        ).forEach {
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
    fun consultas_usanVisibleAnyYLaRamaDeBandeja() {
        listOf("observePinned", "observeAssigned", "observeSuggestions", "observeDated", "observeOverdueCount")
            .forEach { name ->
                assertTrue("$name: condición de visibilidad compartida", querySource(name).contains("VISIBLE_ANY"))
            }
        val visible = constantSource("VISIBLE_ANY")
        assertTrue("tarea borrada", visible.contains("t.deletedAt IS NULL"))
        assertTrue("columna borrada", visible.contains("c.deletedAt IS NULL"))
        assertTrue("tablero borrado", visible.contains("b.deletedAt IS NULL"))
        assertTrue("bandeja visible", visible.contains("t.columnId IS NULL OR"))
        assertFalse("la visibilidad ya no decide la completada", visible.contains("completedAt"))
        assertFalse("ya no depende de la columna de cierre", visible.contains("isDoneColumn"))
        assertEquals("t.completedAt IS NULL", constantValue("OPEN"))
        assertTrue(constantValue("OPEN_OR_DONE_TODAY").contains("t.completedAt >= :dayStart"))
    }

    @Test
    fun consultas_incluyenTareasSinColumnaDeCualquierLista() {
        val visible = constantSource("VISIBLE_ANY")

        assertTrue("las tareas sin columna son visibles sea cual sea su lista", visible.contains("t.columnId IS NULL OR"))
        assertFalse("la visibilidad no depende de la lista", visible.contains("listId"))
        assertFalse(visible.contains("task_lists"))
    }

    @Test
    fun consultas_unenTaskLists() {
        assertTrue(constantValue("FROM_TASKS").contains("LEFT JOIN task_lists l ON t.listId = l.id"))
        listOf("observePinned", "observeSuggestions").forEach { name ->
            assertTrue("$name une task_lists", querySource(name).contains("LEFT JOIN task_lists l ON t.listId = l.id"))
        }
        listOf("observePinned", "observeAssigned", "observeSuggestions", "observeDated").forEach { name ->
            assertTrue("$name expone listName", querySource(name).contains("l.name AS listName"))
        }
    }

    @Test
    fun pinned_exigeMarcaDeHoyYCompletadaHoyOPendiente() {
        val pinned = querySource("observePinned")

        assertTrue(pinned.contains("m.addedAt >= :dayStart"))
        assertTrue(pinned.contains("OPEN_OR_DONE_TODAY"))
        assertTrue(querySource("observeMarkedIds").contains("addedAt >= :dayStart"))
    }

    @Test
    fun assigned_limitaDueAtAFinDeHoy() {
        val assigned = querySource("observeAssigned")

        assertTrue(assigned.contains("t.dueAt < :dayEnd"))
        assertTrue(assigned.contains("t.dueAt IS NOT NULL"))
        assertTrue(assigned.contains("OPEN_OR_DONE_TODAY"))
    }

    @Test
    fun suggestions_exigeMarcaAnteriorYPendiente() {
        val suggestions = querySource("observeSuggestions")

        assertTrue(suggestions.contains("m.addedAt < :dayStart"))
        assertTrue(suggestions.contains("m.deletedAt IS NULL"))
        assertTrue(suggestions.contains("+ OPEN +"))
        assertTrue(suggestions.contains("ORDER BY m.addedAt DESC, t.id ASC"))
    }

    @Test
    fun observeDated_filtraCompletadasYExigeFecha() {
        val dated = querySource("observeDated")

        assertTrue(dated.contains("+ OPEN +"))
        assertTrue(dated.contains("t.dueAt IS NOT NULL"))
        assertTrue(dated.contains("t.assigneeId = :assigneeId"))
        assertTrue(dated.contains("ORDER BY t.dueAt ASC, t.id ASC"))
    }

    @Test
    fun overdueCount_filtraCompletadasExplicitamente() {
        val count = querySource("observeOverdueCount")

        assertTrue(count.contains("+ OPEN +"))
        assertFalse("el conteo no cuenta completadas de hoy", count.contains("OPEN_OR_DONE_TODAY"))
    }

    @Test
    fun consultas_usanLeftJoinYRamaDeBandeja() {
        val from = constantSource("FROM_TASKS")

        assertTrue(from.contains("LEFT JOIN columns c ON t.columnId = c.id"))
        assertTrue(from.contains("LEFT JOIN boards b ON c.boardId = b.id"))
                assertTrue(querySource("observePinned").contains("LEFT JOIN columns c ON t.columnId = c.id"))
        assertTrue(querySource("observeAssigned").contains("FROM_TASKS"))
        assertTrue(querySource("observeOverdueCount").contains("FROM_TASKS"))
        assertTrue(querySource("observeDated").contains("FROM_TASKS"))
    }

    @Test
    fun consultas_exigenColumnaYTableroExistentesParaTareasConColumna() {
        assertTrue(constantSource("VISIBLE_ANY").contains("c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL"))
    }

    @Test
    fun ordenes_sonEstablesConDesempatePorId() {
        assertTrue(querySource("observePinned").contains("ORDER BY m.addedAt DESC, t.id ASC"))
        assertTrue(querySource("observeAssigned").contains("ORDER BY t.dueAt ASC, t.id ASC"))
        assertTrue(querySource("observeSuggestions").contains("ORDER BY m.addedAt DESC, t.id ASC"))
    }

    @Test
    fun myDayDao_noBorraFisicamente() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
        assertTrue(source.contains("OnConflictStrategy.REPLACE"))
    }

    private fun constantSource(name: String): String {
        val start = source.indexOf("private const val $name =")
        assertTrue("falta la constante $name", start >= 0)
        return source.substring(start, source.indexOf("\n\n", start).takeIf { it > 0 } ?: source.length)
    }

    private fun constantValue(name: String): String =
        constantSource(name).substringAfter("=").trim().removeSurrounding("\"")

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
