package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDayItem
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.myday.applyMyDay
import com.simposio.taskeer.domain.myday.filterMyDay
import com.simposio.taskeer.domain.myday.mergeMyDay
import com.simposio.taskeer.domain.myday.sortMyDay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyDayRulesTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")

    @Test
    fun merge_soloMarcada_unElementoMarcadoNoAsignado() {
        val items = mergeMyDay(MyDay(listOf(entry("p1", addedAt = now)), emptyList()))

        assertEquals(listOf("p1"), items.map { it.task.id })
        assertTrue(items.single().isMarked)
        assertFalse(items.single().isAssigned)
    }

    @Test
    fun merge_soloAsignada_unElementoAsignadoNoMarcado() {
        val items = mergeMyDay(MyDay(emptyList(), listOf(entry("a1", dueAt = now))))

        assertFalse(items.single().isMarked)
        assertTrue(items.single().isAssigned)
    }

    @Test
    fun merge_marcadaYAsignada_unSoloElementoConAmbosOrigenes() {
        val items = mergeMyDay(
            MyDay(listOf(entry("t1", addedAt = now)), listOf(entry("t1", dueAt = now.plusSeconds(5)))),
        )

        assertEquals(1, items.size)
        assertTrue(items.single().isMarked && items.single().isAssigned)
        assertEquals(now, items.single().markedAt)
    }

    @Test
    fun merge_vacio_listaVacia() {
        assertTrue(mergeMyDay(MyDay(emptyList(), emptyList())).isEmpty())
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
    fun filter_all_devuelveTodo() {
        val items = listOf(item("m", markedAt = now), item("a", isAssigned = true))

        assertEquals(listOf("m", "a"), filterMyDay(items, MyDayFilter.ALL, now).ids())
    }

    @Test
    fun filter_marked_soloMarcadas() {
        val items = listOf(item("m", markedAt = now), item("a", isAssigned = true), item("ma", markedAt = now, isAssigned = true))

        assertEquals(listOf("m", "ma"), filterMyDay(items, MyDayFilter.MARKED, now).ids())
    }

    @Test
    fun filter_assigned_soloAsignadas() {
        val items = listOf(item("m", markedAt = now), item("a", isAssigned = true), item("ma", markedAt = now, isAssigned = true))

        assertEquals(listOf("a", "ma"), filterMyDay(items, MyDayFilter.ASSIGNED, now).ids())
    }

    @Test
    fun filter_marcadaYAsignada_apareceBajoAmbosFiltros() {
        val both = item("ma", markedAt = now, isAssigned = true, dueAt = now.plusSeconds(1))

        assertEquals(listOf("ma"), filterMyDay(listOf(both), MyDayFilter.MARKED, now).ids())
        assertEquals(listOf("ma"), filterMyDay(listOf(both), MyDayFilter.ASSIGNED, now).ids())
    }

    @Test
    fun filter_overdue_soloAsignadasConFechaPasadaEstricta() {
        val items = listOf(
            item("vencida", isAssigned = true, dueAt = now.minusSeconds(1)),
            item("igual", isAssigned = true, dueAt = now),
            item("futura", isAssigned = true, dueAt = now.plusSeconds(1)),
            item("marcada-vencida", markedAt = now, dueAt = now.minusSeconds(1)),
        )

        assertEquals(listOf("vencida"), filterMyDay(items, MyDayFilter.OVERDUE, now).ids())
    }

    @Test
    fun filter_combinadoConOrden() {
        val items = listOf(
            item("a", TaskPriority.LOW, markedAt = now),
            item("b", TaskPriority.URGENT, markedAt = now),
            item("c", TaskPriority.URGENT, isAssigned = true),
        )

        assertEquals(listOf("b", "a"), applyMyDay(items, MyDayFilter.MARKED, MyDaySort.PRIORITY, now).ids())
    }

    @Test
    fun overdueFilter_coincideConConteo() {
        val assigned = (1..7).map { entry("v$it", dueAt = now.minusSeconds(it.toLong())) } +
            entry("f1", dueAt = now.plusSeconds(10))
        val items = mergeMyDay(MyDay(listOf(entry("p", addedAt = now, dueAt = now.minusSeconds(5))), assigned))

        assertEquals(7, filterMyDay(items, MyDayFilter.OVERDUE, now).size)
    }

    @Test
    fun filtro_soloTieneLosOrigenesActuales() {
        assertEquals(
            listOf("ALL", "MARKED", "ASSIGNED", "OVERDUE"),
            MyDayFilter.entries.map { it.name },
        )
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
        isAssigned: Boolean = false,
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
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
        markedAt = markedAt,
        isAssigned = isAssigned,
    )
}
