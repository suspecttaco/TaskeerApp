package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskList
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.search.SearchIndex
import com.simposio.taskeer.domain.search.SearchResults
import com.simposio.taskeer.domain.search.SearchRules
import com.simposio.taskeer.domain.search.SearchableTask
import com.simposio.taskeer.domain.search.search
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRulesTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")

    private fun sync(id: String, deleted: Boolean = false) =
        SyncMetadata(id, now, pending = false, deletedAt = if (deleted) now else null)

    private fun board(id: String, name: String, deleted: Boolean = false) =
        Board(name, now, sync(id, deleted))

    private fun list(id: String, name: String, position: Int = 0, deleted: Boolean = false) =
        TaskList(name, position, now, sync(id, deleted))

    private fun column(id: String, boardId: String, name: String, position: Int = 0, deleted: Boolean = false) =
        Column(boardId, name, position, null, false, now, sync(id, deleted))

    private fun task(
        id: String,
        title: String,
        description: String? = null,
        completed: Boolean = false,
        deleted: Boolean = false,
        listId: String? = null,
    ) = SearchableTask(
        task = Task(
            columnId = null,
            listId = listId,
            title = title,
            description = description,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = now,
            completedAt = if (completed) now else null,
            sync = sync(id, deleted),
        ),
        boardId = null,
        boardName = null,
        columnName = null,
        listName = "Tareas",
    )

    private fun index(
        boards: List<Board> = emptyList(),
        lists: List<TaskList> = emptyList(),
        columns: List<Column> = emptyList(),
        tasks: List<SearchableTask> = emptyList(),
    ) = SearchIndex(boards, lists, columns, tasks)

    private fun SearchResults.taskIds() = tasks.map { it.id }

    @Test
    fun ignoraMayusculasYAcentos() {
        val data = index(
            boards = listOf(board("b1", "proyecto")),
            tasks = listOf(task("t1", "Canción de cuna")),
        )

        assertEquals(listOf("b1"), search("PROYECTO", data).boards.map { it.id })
        assertEquals(listOf("t1"), search("cancion", data).taskIds())
    }

    @Test
    fun laEnneSeReduceALaN() {
        val data = index(tasks = listOf(task("t1", "El niño")))

        assertEquals(listOf("t1"), search("nino", data).taskIds())
    }

    @Test
    fun variosTerminos_enCualquierOrden() {
        val data = index(tasks = listOf(task("t1", "Leche: compra semanal")))

        assertEquals(listOf("t1"), search("compra leche", data).taskIds())
    }

    @Test
    fun terminoNoPresente_noCoincide() {
        val data = index(tasks = listOf(task("t1", "Leche: compra semanal")))

        assertTrue(search("compra pan", data).isEmpty)
    }

    @Test
    fun buscaEnLaDescripcionDeLaTarea() {
        val data = index(tasks = listOf(task("t1", "Reunión", description = "Preparar presupuesto")))

        val hit = search("presupuesto", data).tasks.single()

        assertEquals("t1", hit.id)
        assertFalse(hit.inTitle)
    }

    @Test
    fun consultaDeSoloEspacios_noCoincide() {
        val data = index(boards = listOf(board("b1", "Casa")), tasks = listOf(task("t1", "Casa")))

        assertTrue(search("   ", data).isEmpty)
    }

    @Test
    fun recortaLaConsulta() {
        val data = index(boards = listOf(board("b1", "Casa")))

        assertEquals(listOf("b1"), search("  casa  ", data).boards.map { it.id })
    }

    @Test
    fun agrupaEnTablerosListasColumnasYTareas() {
        val data = index(
            boards = listOf(board("b1", "Viaje")),
            lists = listOf(list("l1", "Viaje largo")),
            columns = listOf(column("c1", "b1", "Viaje pendiente")),
            tasks = listOf(task("t1", "Reservar viaje")),
        )

        val results = search("viaje", data)

        assertEquals(listOf("b1"), results.boards.map { it.id })
        assertEquals(listOf("l1"), results.lists.map { it.id })
        assertEquals(listOf("c1"), results.columns.map { it.id })
        assertEquals(listOf("t1"), results.taskIds())
        assertEquals("Viaje", results.columns.single().boardName)
        assertEquals(4, results.total)
    }

    @Test
    fun ordenaTableros() {
        val data = index(boards = listOf(board("b1", "Zeta casa"), board("b2", "Árbol casa"), board("b3", "beta casa")))

        assertEquals(listOf("b2", "b3", "b1"), search("casa", data).boards.map { it.id })
    }

    @Test
    fun listaCoincidePorNombre() {
        val data = index(lists = listOf(list("l1", "Compras"), list("l2", "Trabajo")))

        assertEquals(listOf("l1"), search("COMPRAS", data).lists.map { it.id })
    }

    @Test
    fun listaNoCoincidePorElTextoDeSusTareas() {
        val data = index(
            lists = listOf(list("l1", "Compras")),
            tasks = listOf(task("t1", "Comprar leche", listId = "l1")),
        )

        val results = search("leche", data)

        assertTrue(results.lists.isEmpty())
        assertEquals(listOf("t1"), results.taskIds())
    }

    @Test
    fun ordenaListasPorPosicion() {
        val data = index(
            lists = listOf(
                list("l1", "Zeta casa", position = 0),
                list("l2", "Alfa casa", position = 2),
                list("l3", "Beta casa", position = 2),
                list("l4", "Alfa casa", position = 1),
            ),
        )

        assertEquals(listOf("l1", "l4", "l2", "l3"), search("casa", data).lists.map { it.id })
    }

    @Test
    fun listasBorradas_seIgnoran() {
        val data = index(lists = listOf(list("l1", "Compras", deleted = true)))

        assertTrue(search("compras", data).isEmpty)
    }

    @Test
    fun listaCuentaSoloTareasAbiertas() {
        val data = index(
            lists = listOf(list("l1", "Compras"), list("l2", "Compras vacia")),
            tasks = listOf(
                task("t1", "a", listId = "l1"),
                task("t2", "b", listId = "l1"),
                task("t3", "c", listId = "l1", completed = true),
                task("t4", "d", listId = "l9"),
            ),
        )

        val counts = search("compras", data).lists.associate { it.id to it.openTasks }

        assertEquals(mapOf("l1" to 2, "l2" to 0), counts)
    }

    @Test
    fun ordenaColumnasPorTableroYPosicion() {
        val data = index(
            boards = listOf(board("b1", "Beta"), board("b2", "Alfa")),
            columns = listOf(
                column("c1", "b1", "x", position = 0),
                column("c2", "b2", "x", position = 2),
                column("c3", "b2", "x", position = 1),
            ),
        )

        assertEquals(listOf("c3", "c2", "c1"), search("x", data).columns.map { it.id })
    }

    @Test
    fun tareasConTituloVanAntesQueSoloDescripcion() {
        val data = index(
            tasks = listOf(
                task("t1", "Aaa", description = "informe"),
                task("t2", "Informe final"),
            ),
        )

        assertEquals(listOf("t2", "t1"), search("informe", data).taskIds())
    }

    @Test
    fun abiertasAntesQueCompletadas() {
        val data = index(
            tasks = listOf(
                task("t1", "Informe a", completed = true),
                task("t2", "Informe b"),
            ),
        )

        assertEquals(listOf("t2", "t1"), search("informe", data).taskIds())
    }

    @Test
    fun ordenEstableConEmpates() {
        val data = index(
            boards = listOf(board("b2", "Casa"), board("b1", "casa")),
            tasks = listOf(task("t2", "Casa"), task("t1", "casa")),
        )

        val results = search("casa", data)

        assertEquals(listOf("b1", "b2"), results.boards.map { it.id })
        assertEquals(listOf("t1", "t2"), results.taskIds())
    }

    @Test
    fun resultsVacios_esEmpty() {
        assertTrue(SearchResults.Empty.isEmpty)
        assertEquals(0, SearchResults.Empty.total)
        assertTrue(search("nada", index()).isEmpty)
    }

    @Test
    fun columnasDeTableroBorrado_seIgnoran() {
        val data = index(
            boards = listOf(board("b1", "Viejo", deleted = true)),
            columns = listOf(column("c1", "b1", "Columna")),
            tasks = listOf(task("t1", "Columna borrada", deleted = true)),
        )

        assertTrue(search("columna", data).isEmpty)
    }

    @Test
    fun debounce_es250Milisegundos() {
        assertEquals(250L, SearchRules.DEBOUNCE_MILLIS)
    }
}
