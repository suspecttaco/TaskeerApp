package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayItem
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.myday.applyMyDay
import com.simposio.taskeer.domain.myday.mergeMyDay
import com.simposio.taskeer.domain.myday.sortMyDay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyDayRulesTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")

    @Test
    fun merge_soloMarcada_unElementoMarcado() {
        val items = mergeMyDay(MyDay(listOf(entry("p1", addedAt = now))))

        assertEquals(listOf("p1"), items.map { it.task.id })
        assertTrue(items.single().isMarked)
        assertEquals(now, items.single().markedAt)
    }

    @Test
    fun merge_vacio_listaVacia() {
        assertTrue(mergeMyDay(MyDay(emptyList())).isEmpty())
    }

    @Test
    fun sort_porFecha_sinFechaAlFinal() {
        val items = listOf(item("sin"), item("tarde", dueAt = now.plusSeconds(60)), item("pronto", dueAt = now))

        assertEquals(listOf("pronto", "tarde", "sin"), sortMyDay(items, MyDaySort.DUE_DATE).ids())
    }

    @Test
    fun sort_porPrioridad_urgenteAPrimeroConDesempatePorFecha() {
        val items = listOf(
            item("baja", TaskPriority.LOW, dueAt = now),
            item("alta-tarde", TaskPriority.HIGH, dueAt = now.plusSeconds(60)),
            item("alta-sin", TaskPriority.HIGH),
            item("urgente", TaskPriority.URGENT, dueAt = now.plusSeconds(600)),
            item("alta-pronto", TaskPriority.HIGH, dueAt = now),
            item("media", TaskPriority.MEDIUM),
        )

        assertEquals(
            listOf("urgente", "alta-pronto", "alta-tarde", "alta-sin", "media", "baja"),
            sortMyDay(items, MyDaySort.PRIORITY).ids(),
        )
    }

    @Test
    fun sort_marcadoReciente_masRecientePrimeroYNoMarcadasAlFinal() {
        val items = listOf(
            item("no-marcada"),
            item("vieja", markedAt = now),
            item("nueva", markedAt = now.plusSeconds(60)),
        )

        assertEquals(listOf("nueva", "vieja", "no-marcada"), sortMyDay(items, MyDaySort.MARKED_AT).ids())
    }

    @Test
    fun sort_desempatePorId() {
        val items = listOf(item("b", dueAt = now), item("a", dueAt = now), item("c", dueAt = now))

        assertEquals(listOf("a", "b", "c"), sortMyDay(items, MyDaySort.DUE_DATE).ids())
    }

    @Test
    fun tareaDeBandeja_conservaBoardNameNulo() {
        val inbox = MyDayTask(item("pan").task.copy(columnId = null), boardId = null, boardName = null, addedAt = Instant.EPOCH)

        val merged = mergeMyDay(MyDay(pinned = listOf(inbox)))

        assertNull(merged.single().boardName)
        assertNull(merged.single().boardId)
    }

    @Test
    fun sort_completadasVanAlFinalEnCadaCriterio() {
        val items = listOf(
            item("hecha-urgente", TaskPriority.URGENT, dueAt = now, markedAt = now.plusSeconds(99), completedAt = now),
            item("pendiente-baja", TaskPriority.LOW, dueAt = now.plusSeconds(600), markedAt = now),
            item("hecha-sin-fecha", completedAt = now),
        )

        MyDaySort.entries.forEach { sort ->
            val ids = sortMyDay(items, sort).ids()

            assertEquals(sort.name, "pendiente-baja", ids.first())
            assertEquals(sort.name, setOf("hecha-urgente", "hecha-sin-fecha"), ids.drop(1).toSet())
        }
    }

    @Test
    fun sort_completadas_conservanElOrdenElegidoEntreSi() {
        val items = listOf(
            item("hecha-tarde", dueAt = now.plusSeconds(60), completedAt = now),
            item("pendiente"),
            item("hecha-pronto", dueAt = now, completedAt = now),
        )

        assertEquals(listOf("pendiente", "hecha-pronto", "hecha-tarde"), sortMyDay(items, MyDaySort.DUE_DATE).ids())
    }

    @Test
    fun apply_ordenaSinFiltrar() {
        val items = listOf(item("b", dueAt = now.plusSeconds(60)), item("a", dueAt = now))

        assertEquals(listOf("a", "b"), applyMyDay(items, MyDaySort.DUE_DATE).ids())
    }

    private fun List<MyDayItem>.ids(): List<String> = map { it.task.id }

    private fun entry(
        id: String,
        addedAt: Instant? = null,
        dueAt: Instant? = null,
    ): MyDayTask = MyDayTask(item(id, dueAt = dueAt).task, "b1", "Tablero", addedAt)

    private fun item(
        id: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Instant? = null,
        markedAt: Instant? = null,
        completedAt: Instant? = null,
    ): MyDayItem = MyDayItem(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = priority,
            dueAt = dueAt,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = completedAt,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
        markedAt = markedAt,
    )
}
