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
            "observeMarkedIds", "observePinned", "observeSuggestions", "observeSuggestionCount",
            "observeOpen", "observeOverdueCount",
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
        listOf("observePinned", "observeOpen", "observeOverdueCount").forEach { name ->
            assertTrue("$name: condición de visibilidad compartida", querySource(name).contains("VISIBLE_ANY"))
        }
        listOf("observeSuggestions", "observeSuggestionCount").forEach { name ->
            assertTrue("$name comparte el predicado", querySource(name).contains("SUGGESTION_FROM_WHERE"))
        }
        assertTrue(constantSource("SUGGESTION_FROM_WHERE").contains("VISIBLE_ANY"))
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

        assertTrue("las tareas sin columna siguen siendo visibles", visible.contains("t.columnId IS NULL OR"))
        assertTrue(
            "una tarea de lista solo se ve si su lista existe y está viva (R14)",
            visible.contains("t.listId IS NULL OR (l.id IS NOT NULL AND l.deletedAt IS NULL)"),
        )
    }

    @Test
    fun consultas_unenTaskLists() {
        assertTrue(constantValue("FROM_TASKS").contains("LEFT JOIN task_lists l ON t.listId = l.id"))
        assertTrue(querySource("observePinned").contains("LEFT JOIN task_lists l ON t.listId = l.id"))
        assertTrue(constantValue("SUGGESTION_FROM_WHERE").contains("FROM_TASKS"))
        listOf("observePinned", "observeSuggestions", "observeOpen").forEach { name ->
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
    fun suggestions_predicadoYOrden() {
        val where = constantSource("SUGGESTION_FROM_WHERE")
        val rank = constantSource("SUGGESTION_RANK")
        val query = querySource("observeSuggestions")

        assertTrue(where.contains("LEFT JOIN my_day_tasks m ON m.id = t.id"))
        assertTrue(where.contains("+ OPEN +"))
        assertTrue("no marcada hoy", where.contains("(m.id IS NULL OR m.deletedAt IS NOT NULL OR m.addedAt < :dayStart)"))
        assertTrue(
            "no descartada hoy",
            where.contains(
                "(m.id IS NULL OR m.deletedAt IS NULL OR m.addedAt <> \$DISMISSED_ADDED_AT OR m.deletedAt < :dayStart)",
            ),
        )
        assertTrue("asignada o marca anterior", where.contains("t.assigneeId = :assigneeId OR (m.id IS NOT NULL AND m.deletedAt IS NULL)"))
        assertFalse("la silenciada no depende de dueAt", where.contains("dueAt"))
        assertTrue(rank.contains("t.dueAt < :dayStart THEN 0"))
        assertTrue(rank.contains("t.dueAt < :dayEnd THEN 1"))
        assertTrue(rank.contains("THEN 2 ELSE 3"))
        assertTrue(query.contains("SUGGESTION_RANK"))
        assertTrue(query.contains("-m.addedAt"))
        assertTrue("sin fecha por updatedAt descendente", query.contains("= 3 AND t.dueAt IS NULL THEN -t.updatedAt END ASC, t.id ASC"))
        assertTrue(query.contains("CASE WHEN m.deletedAt IS NULL THEN m.addedAt END AS addedAt"))
    }

    @Test
    fun suggestions_silenciaSoloConCentinelaYDeHoy() {
        val where = constantSource("SUGGESTION_FROM_WHERE")

        assertTrue(where.contains("m.addedAt <> \$DISMISSED_ADDED_AT OR m.deletedAt < :dayStart"))
        assertTrue(
            "quitada hoy es candidata",
            where.contains("(m.id IS NOT NULL AND m.addedAt <> \$DISMISSED_ADDED_AT AND m.deletedAt >= :dayStart)"),
        )
        assertEquals(0L, DISMISSED_ADDED_AT)
    }

    @Test
    fun suggestionCount_compartePredicadoYEsCountSinLimite() {
        val count = querySource("observeSuggestionCount")

        assertTrue(count.contains("SELECT COUNT(*)"))
        assertTrue(count.contains("SUGGESTION_FROM_WHERE"))
        assertFalse(count.contains("dayEnd"))
        assertFalse(count.uppercase().contains("LIMIT"))
        assertFalse(source.uppercase().contains("LIMIT"))
    }

    @Test
    fun observeAssigned_yaNoExiste() {
        assertFalse(source.contains("observeAssigned"))
    }

    @Test
    fun observeOpen_usaVisibleAnyYOpenSinExigirFecha() {
        val open = querySource("observeOpen")

        assertTrue(open.contains("VISIBLE_ANY"))
        assertTrue(open.contains("+ OPEN +"))
        assertTrue(open.contains("t.assigneeId = :assigneeId"))
        assertFalse(open.contains("dueAt IS NOT NULL"))
    }

    @Test
    fun observeOpen_ordenaPorDueAtYId() {
        assertTrue(querySource("observeOpen").contains("ORDER BY t.dueAt ASC, t.id ASC"))
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
        assertTrue(querySource("observeOverdueCount").contains("FROM_TASKS"))
        assertTrue(querySource("observeOpen").contains("FROM_TASKS"))
    }

    @Test
    fun consultas_exigenColumnaYTableroExistentesParaTareasConColumna() {
        assertTrue(constantSource("VISIBLE_ANY").contains("c.id IS NOT NULL AND c.deletedAt IS NULL AND b.id IS NOT NULL"))
    }

    @Test
    fun ordenes_sonEstablesConDesempatePorId() {
        assertTrue(querySource("observePinned").contains("ORDER BY m.addedAt DESC, t.id ASC"))
        assertTrue(querySource("observeSuggestions").contains("-t.updatedAt END ASC, t.id ASC"))
    }

    @Test
    fun myDayDao_noBorraFisicamente() {
        assertFalse(source.contains("@Delete"))
        assertFalse(source.contains("DELETE FROM"))
        assertTrue(source.contains("OnConflictStrategy.REPLACE"))
    }

    private fun constantSource(name: String): String {
        val start = source.indexOf("const val $name =")
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
