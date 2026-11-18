package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.groupPlanned
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedRulesTest {

    private val utc = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-11-17T15:00:00Z")
    private val window = dayWindowAt(now, utc)

    @Test
    fun group_clasificaVencidasHoyMananaYProximos() {
        val view = groupPlanned(
            listOf(
                item("ayer", "2026-11-16T09:00:00Z"),
                item("hoy", "2026-11-17T20:00:00Z"),
                item("manana", "2026-11-18T09:00:00Z"),
                item("lejos", "2026-11-25T09:00:00Z"),
            ),
            window,
            utc,
        )

        assertEquals(listOf("ayer"), view.overdue.ids())
        assertEquals(listOf("hoy"), view.today.ids())
        assertEquals(listOf("manana"), view.tomorrow.ids())
        assertEquals(listOf("lejos"), view.upcoming.single().items.ids())
    }

    @Test
    fun group_tareaDeHoyYaVencidaPorHora_vaEnHoy() {
        val view = groupPlanned(listOf(item("temprano", "2026-11-17T08:00:00Z")), window, utc)

        assertEquals(listOf("temprano"), view.today.ids())
        assertTrue(view.overdue.isEmpty())
    }

    @Test
    fun group_proximosAgrupadosPorFechaAscendente() {
        val view = groupPlanned(
            listOf(
                item("c", "2026-12-01T09:00:00Z"),
                item("a", "2026-11-19T09:00:00Z"),
                item("a2", "2026-11-19T18:00:00Z"),
                item("b", "2026-11-20T09:00:00Z"),
            ),
            window,
            utc,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 1)),
            view.upcoming.map { it.date },
        )
        assertEquals(listOf("a", "a2"), view.upcoming.first().items.ids())
    }

    @Test
    fun group_ordenaPorDueAtYDesempataPorId() {
        val view = groupPlanned(
            listOf(
                item("b", "2026-11-17T20:00:00Z"),
                item("z", "2026-11-17T18:00:00Z"),
                item("a", "2026-11-17T20:00:00Z"),
            ),
            window,
            utc,
        )

        assertEquals(listOf("z", "a", "b"), view.today.ids())
    }

    @Test
    fun group_mismaTareaEnDosZonas_cambiaDeGrupo() {
        val task = item("t", "2026-11-17T23:30:00Z")
        val madrid = ZoneId.of("Europe/Madrid")

        val inUtc = groupPlanned(listOf(task), dayWindowAt(now, utc), utc)
        val inMadrid = groupPlanned(listOf(task), dayWindowAt(now, madrid), madrid)

        assertEquals(listOf("t"), inUtc.today.ids())
        assertEquals(listOf("t"), inMadrid.tomorrow.ids())
    }

    @Test
    fun group_alCambiarLaVentana_reclasifica() {
        val items = listOf(item("t", "2026-11-18T09:00:00Z"))

        val before = groupPlanned(items, window, utc)
        val after = groupPlanned(items, dayWindowAt(window.end, utc), utc)

        assertEquals(listOf("t"), before.tomorrow.ids())
        assertEquals(listOf("t"), after.today.ids())
    }

    @Test
    fun group_listaVacia_devuelveGruposVacios() {
        val view = groupPlanned(emptyList(), window, utc)

        assertTrue(view.overdue.isEmpty() && view.today.isEmpty() && view.tomorrow.isEmpty() && view.upcoming.isEmpty())
    }

    private fun List<PlannedItem>.ids(): List<String> = map { it.task.id }

    private fun item(id: String, dueAt: String): PlannedItem = PlannedItem(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = Instant.parse(dueAt),
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
    )
}
