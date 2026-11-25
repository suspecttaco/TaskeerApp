package com.simposio.taskeer.ui.search

import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.search.SearchableTask
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRowRulesTest {

    private fun searchable(boardName: String?, columnName: String?, listName: String?) = SearchableTask(
        task = Task(
            columnId = columnName?.let { "c1" },
            title = "Informe",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata("t1", Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = boardName?.let { "b1" },
        boardName = boardName,
        columnName = columnName,
        listName = listName,
    )

    @Test
    fun contextoDeTarea_enTablero() {
        assertEquals(
            TaskContext.InBoard("Casa", "Por hacer"),
            taskContext(searchable("Casa", "Por hacer", null)),
        )
    }

    @Test
    fun contextoDeTarea_enLista() {
        assertEquals(TaskContext.InList("Compras"), taskContext(searchable(null, null, "Compras")))
    }

    @Test
    fun contextoDeTarea_enBandeja() {
        assertEquals(TaskContext.InList("Tareas"), taskContext(searchable(null, null, "Tareas")))
    }

    @Test
    fun descripcionDeAccesibilidad_incluyeTipoYContexto() {
        assertEquals(
            "Tarea: Informe, Casa › Por hacer",
            rowDescription("Tarea", "Informe", "Casa › Por hacer", null),
        )
        assertEquals("Tablero: Casa", rowDescription("Tablero", "Casa", null, null))
    }

    @Test
    fun descripcionDeAccesibilidad_marcaCompletada() {
        assertEquals(
            "Tarea: Informe, Compras, completada",
            rowDescription("Tarea", "Informe", "Compras", "Completada"),
        )
    }

    @Test
    fun contextoDeLista_cuentaAbiertas() {
        val strings = strings()
        val plural = strings.substringAfter("<plurals name=\"search_list_open_tasks\">").substringBefore("</plurals>")

        assertTrue(plural.contains("<item quantity=\"one\">%1\$d tarea abierta</item>"))
        assertTrue(plural.contains("<item quantity=\"other\">%1\$d tareas abiertas</item>"))
        assertTrue(strings.contains("<string name=\"search_list_no_open_tasks\">Sin tareas abiertas</string>"))
    }

    @Test
    fun descripcionDeAccesibilidad_deLista() {
        assertEquals("Lista: Compras, 3 tareas abiertas", rowDescription("Lista", "Compras", "3 tareas abiertas", null))
    }

    @Test
    fun resumen_pluralizaResultados() {
        val strings = strings()
        val plural = strings.substringAfter("<plurals name=\"search_summary\">").substringBefore("</plurals>")

        assertTrue(plural.contains("<item quantity=\"one\">%1\$d resultado</item>"))
        assertTrue(plural.contains("<item quantity=\"other\">%1\$d resultados</item>"))
    }

    private fun strings(): String {
        val relative = "app/src/main/res/values/strings.xml"
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
