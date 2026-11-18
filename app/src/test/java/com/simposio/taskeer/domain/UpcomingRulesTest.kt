package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.AddTarget
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.domain.planned.PlannedGroup
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.addTargetDate
import com.simposio.taskeer.domain.planned.dayLabelFor
import com.simposio.taskeer.domain.planned.groupAllTasks
import com.simposio.taskeer.domain.planned.groupUpcoming
import com.simposio.taskeer.domain.planned.toggleCollapsed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingRulesTest {

    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun groupUpcoming_devuelveSieteDiasConsecutivos() {
        val days = groupUpcoming(emptyList(), today, utc)

        assertEquals((0L..6L).map { today.plusDays(it) }, days.map { it.date })
    }

    @Test
    fun groupUpcoming_etiquetasHoyMananaYDiaDeLaSemana() {
        val labels = groupUpcoming(emptyList(), today, utc).map { it.label }

        assertEquals(DayLabel.Today, labels[0])
        assertEquals(DayLabel.Tomorrow, labels[1])
        assertEquals(DayLabel.Weekday(today.plusDays(2)), labels[2])
        assertEquals(DayLabel.Weekday(today.plusDays(6)), labels[6])
    }

    @Test
    fun groupUpcoming_incluyeVencidasEnHoy() {
        val days = groupUpcoming(listOf(item("ayer", "2026-11-16T09:00:00Z")), today, utc)

        assertEquals(listOf("ayer"), days[0].items.ids())
    }

    @Test
    fun groupUpcoming_vencidasVanAntesQueLasDeHoy() {
        val days = groupUpcoming(
            listOf(item("hoy", "2026-11-17T01:00:00Z"), item("ayer", "2026-11-16T23:00:00Z")),
            today,
            utc,
        )

        assertEquals(listOf("ayer", "hoy"), days[0].items.ids())
    }

    @Test
    fun groupUpcoming_excluyeSinFechaYPosteriores() {
        val days = groupUpcoming(
            listOf(item("sin", null), item("lejos", "2026-11-24T09:00:00Z"), item("ultimo", "2026-11-23T09:00:00Z")),
            today,
            utc,
        )

        assertEquals(listOf("ultimo"), days.flatMap { it.items }.ids())
    }

    @Test
    fun groupUpcoming_ordenaPorDueAtYId() {
        val days = groupUpcoming(
            listOf(
                item("b", "2026-11-17T20:00:00Z"),
                item("z", "2026-11-17T18:00:00Z"),
                item("a", "2026-11-17T20:00:00Z"),
            ),
            today,
            utc,
        )

        assertEquals(listOf("z", "a", "b"), days[0].items.ids())
    }

    @Test
    fun groupUpcoming_usaLaFechaLocalNoElInstante() {
        val madrid = ZoneId.of("Europe/Madrid")

        val days = groupUpcoming(listOf(item("t", "2026-11-17T23:30:00Z")), today, madrid)

        assertTrue(days[0].items.isEmpty())
        assertEquals(listOf("t"), days[1].items.ids())
    }

    @Test
    fun groupUpcoming_cambiaConLaFecha() {
        val items = listOf(item("t", "2026-11-18T09:00:00Z"))

        val before = groupUpcoming(items, today, utc)
        val after = groupUpcoming(items, today.plusDays(1), utc)

        assertEquals(listOf("t"), before[1].items.ids())
        assertEquals(listOf("t"), after[0].items.ids())
        assertEquals(DayLabel.Today, after[0].label)
    }

    @Test
    fun groupUpcoming_diasSinTareas_siguenPresentes() {
        val days = groupUpcoming(listOf(item("t", "2026-11-18T09:00:00Z")), today, utc)

        assertEquals(7, days.size)
        assertEquals(6, days.count { it.items.isEmpty() })
    }

    @Test
    fun groupUpcoming_cambioDeHorario_sigueSiendoSieteDias() {
        val madrid = ZoneId.of("Europe/Madrid")
        val beforeShift = LocalDate.of(2026, 10, 24)

        val days = groupUpcoming(listOf(item("t", "2026-10-25T10:00:00Z")), beforeShift, madrid)

        assertEquals((0L..6L).map { beforeShift.plusDays(it) }, days.map { it.date })
        assertEquals(listOf("t"), days[1].items.ids())
    }

    @Test
    fun groupAllTasks_hoyIncluyeVencidas() {
        val view = groupAllTasks(
            listOf(item("ayer", "2026-11-16T09:00:00Z"), item("hoy", "2026-11-17T20:00:00Z")),
            today,
            utc,
        )

        assertEquals(listOf("ayer", "hoy"), view.today.ids())
    }

    @Test
    fun groupAllTasks_mananaEsHoyMasUno() {
        val view = groupAllTasks(listOf(item("m", "2026-11-18T09:00:00Z")), today, utc)

        assertEquals(listOf("m"), view.tomorrow.ids())
    }

    @Test
    fun groupAllTasks_proximamenteDesdePasadoManana() {
        val view = groupAllTasks(
            listOf(item("p", "2026-11-19T09:00:00Z"), item("lejos", "2030-01-01T09:00:00Z")),
            today,
            utc,
        )

        assertEquals(listOf("p", "lejos"), view.upcoming.ids())
    }

    @Test
    fun groupAllTasks_algunDiaSonLasSinFecha() {
        val view = groupAllTasks(listOf(item("s", null), item("h", "2026-11-17T09:00:00Z")), today, utc)

        assertEquals(listOf("s"), view.someday.ids())
    }

    @Test
    fun groupAllTasks_ordenFechadas() {
        val view = groupAllTasks(
            listOf(
                item("b", "2026-11-17T20:00:00Z"),
                item("z", "2026-11-17T18:00:00Z"),
                item("a", "2026-11-17T20:00:00Z"),
            ),
            today,
            utc,
        )

        assertEquals(listOf("z", "a", "b"), view.today.ids())
    }

    @Test
    fun groupAllTasks_algunDiaOrdenaPorCreacionDescendente() {
        val view = groupAllTasks(
            listOf(
                item("viejo", null, createdAt = "2026-11-01T00:00:00Z"),
                item("b", null, createdAt = "2026-11-10T00:00:00Z"),
                item("a", null, createdAt = "2026-11-10T00:00:00Z"),
            ),
            today,
            utc,
        )

        assertEquals(listOf("a", "b", "viejo"), view.someday.ids())
    }

    @Test
    fun groupAllTasks_usaLaFechaLocal() {
        val madrid = ZoneId.of("Europe/Madrid")

        val view = groupAllTasks(listOf(item("t", "2026-11-17T23:30:00Z")), today, madrid)

        assertEquals(listOf("t"), view.tomorrow.ids())
    }

    @Test
    fun groupAllTasks_gruposVacios_siguenPresentes() {
        val view = groupAllTasks(emptyList(), today, utc)

        assertTrue(view.today.isEmpty() && view.tomorrow.isEmpty() && view.upcoming.isEmpty() && view.someday.isEmpty())
    }

    @Test
    fun addTargetDate_hoy() {
        assertEquals(today, addTargetDate(AddTarget.Group(PlannedGroup.TODAY), today))
    }

    @Test
    fun addTargetDate_manana() {
        assertEquals(today.plusDays(1), addTargetDate(AddTarget.Group(PlannedGroup.TOMORROW), today))
    }

    @Test
    fun addTargetDate_diaDeLaSemana_esSuFecha() {
        val date = today.plusDays(4)

        assertEquals(date, addTargetDate(AddTarget.DayOf(date), today))
    }

    @Test
    fun addTargetDate_proximamente_esPasadoManana() {
        assertEquals(today.plusDays(2), addTargetDate(AddTarget.Group(PlannedGroup.UPCOMING), today))
    }

    @Test
    fun addTargetDate_algunDia_esNulo() {
        assertEquals(null, addTargetDate(AddTarget.Group(PlannedGroup.SOMEDAY), today))
    }

    @Test
    fun addTargetDate_seEvaluaConElHoyActual() {
        val target = AddTarget.Group(PlannedGroup.TOMORROW)

        assertEquals(today.plusDays(1), addTargetDate(target, today))
        assertEquals(today.plusDays(2), addTargetDate(target, today.plusDays(1)))
    }

    @Test
    fun dayLabelFor_hoyMananaYDiaDeLaSemana() {
        assertEquals(DayLabel.Today, dayLabelFor(today, today))
        assertEquals(DayLabel.Tomorrow, dayLabelFor(today.plusDays(1), today))
        assertEquals(DayLabel.Weekday(today.plusDays(2)), dayLabelFor(today.plusDays(2), today))
    }

    @Test
    fun toggleCollapsed_alternaPorFecha() {
        val once = toggleCollapsed(emptySet(), "2026-11-17")

        assertEquals(setOf("2026-11-17"), once)
        assertEquals(emptySet<String>(), toggleCollapsed(once, "2026-11-17"))
    }

    private fun List<PlannedItem>.ids(): List<String> = map { it.task.id }

    private fun item(id: String, dueAt: String?, createdAt: String = "1970-01-01T00:00:00Z"): PlannedItem = PlannedItem(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt?.let(Instant::parse),
            assigneeId = null,
            position = 0,
            createdAt = Instant.parse(createdAt),
            completedAt = null,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
    )
}
