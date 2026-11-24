package com.simposio.taskeer.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDayRulesTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val utc = ZoneId.of("UTC")
    private val friday = LocalDate.of(2026, 11, 20)
    private val span = DateSpan(friday, friday.plusDays(3))

    private val work = DeviceCalendar(1, "Trabajo", "yo@example.com", 0xFF0000FF.toInt(), true)
    private val personal = DeviceCalendar(2, "Personal", "yo@example.com", 0xFF00FF00.toInt(), true)

    private fun timed(
        key: String,
        start: String,
        end: String,
        zone: ZoneId = madrid,
        calendarId: Long = 1,
        title: String = "Evento $key",
    ) = DeviceEvent(
        instanceKey = key,
        eventId = key.hashCode().toLong(),
        calendarId = calendarId,
        title = title,
        span = EventSpan.Timed(
            LocalDateTime.parse(start).atZone(zone).toInstant(),
            LocalDateTime.parse(end).atZone(zone).toInstant(),
        ),
    )

    private fun allDay(key: String, start: String, endExclusive: String, calendarId: Long = 1, title: String = "Todo $key") =
        DeviceEvent(
            instanceKey = key,
            eventId = key.hashCode().toLong(),
            calendarId = calendarId,
            title = title,
            span = EventSpan.AllDay(LocalDate.parse(start), LocalDate.parse(endExclusive)),
        )

    private fun days(
        events: List<DeviceEvent> = emptyList(),
        tasks: List<TaskDue> = emptyList(),
        hidden: Set<Long> = emptySet(),
        zone: ZoneId = madrid,
        range: DateSpan = span,
        includeEmpty: Boolean = true,
    ) = buildDays(range, events, listOf(work, personal), hidden, tasks, zone, includeEmpty)

    private fun List<CalendarDay>.on(date: String): CalendarDay = first { it.date == LocalDate.parse(date) }

    private fun CalendarDay.ids(): List<String> = items.map {
        when (it) {
            is DayItem.Event -> it.event.instanceKey
            is DayItem.Task -> "t-${it.task.taskId}"
        }
    }

    @Test
    fun evento_conHora_caeEnSuDia() {
        val result = days(listOf(timed("a", "2026-11-20T10:00", "2026-11-20T11:00")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertTrue(result.on("2026-11-21").items.isEmpty())
        val item = result.on("2026-11-20").items.single() as DayItem.Event
        assertFalse(item.continuesFromPrevious || item.continuesToNext)
    }

    @Test
    fun evento_cruzaLaMedianoche_apareceEnAmbosDiasConMarcasDeContinuacion() {
        val result = days(listOf(timed("a", "2026-11-20T23:00", "2026-11-21T01:00")))

        val first = result.on("2026-11-20").items.single() as DayItem.Event
        val second = result.on("2026-11-21").items.single() as DayItem.Event
        assertTrue(first.continuesToNext && !first.continuesFromPrevious)
        assertTrue(second.continuesFromPrevious && !second.continuesToNext)
    }

    @Test
    fun evento_deDuracionCero_caeSoloEnElDiaDeSuInicio() {
        val result = days(listOf(timed("a", "2026-11-20T23:59", "2026-11-20T23:59")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertTrue(result.on("2026-11-21").items.isEmpty())
    }

    @Test
    fun evento_conHoraDeVariosDias_apareceEnCadaDiaDelRango() {
        val result = days(listOf(timed("a", "2026-11-20T18:00", "2026-11-22T08:00")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertEquals(listOf("a"), result.on("2026-11-21").ids())
        assertEquals(listOf("a"), result.on("2026-11-22").ids())
        val middle = result.on("2026-11-21").items.single() as DayItem.Event
        assertTrue(middle.continuesFromPrevious && middle.continuesToNext)
    }

    @Test
    fun evento_queTerminaALaMedianoche_noApareceEnElDiaSiguiente() {
        val result = days(listOf(timed("a", "2026-11-20T22:00", "2026-11-21T00:00")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertTrue(result.on("2026-11-21").items.isEmpty())
        assertFalse((result.on("2026-11-20").items.single() as DayItem.Event).continuesToNext)
    }

    @Test
    fun todoElDia_deUnSoloDia_caeEnEsaFecha() {
        val result = days(listOf(allDay("a", "2026-11-20", "2026-11-21")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertTrue(result.on("2026-11-21").items.isEmpty())
    }

    @Test
    fun todoElDia_deVariosDias_apareceEnCadaDia() {
        val result = days(listOf(allDay("a", "2026-11-20", "2026-11-23")))

        assertEquals(listOf("a"), result.on("2026-11-20").ids())
        assertEquals(listOf("a"), result.on("2026-11-21").ids())
        assertEquals(listOf("a"), result.on("2026-11-22").ids())
    }

    @Test
    fun todoElDia_conFinNoPosterior_cuentaComoUnDia() {
        val result = days(listOf(allDay("a", "2026-11-21", "2026-11-21")))

        assertEquals(listOf("a"), result.on("2026-11-21").ids())
        assertTrue(result.on("2026-11-20").items.isEmpty())
        assertTrue(result.on("2026-11-22").items.isEmpty())
    }

    @Test
    fun todoElDia_esLaMismaFechaEnCualquierZona() {
        val event = allDay("a", "2026-11-20", "2026-11-21")

        listOf("America/Los_Angeles", "UTC", "Pacific/Auckland").forEach { id ->
            val result = days(listOf(event), zone = ZoneId.of(id), range = DateSpan(friday.minusDays(1), friday.plusDays(2)))

            assertEquals(id, listOf("a"), result.on("2026-11-20").ids())
            assertTrue(id, result.on("2026-11-21").items.isEmpty())
            assertTrue(id, result.on("2026-11-19").items.isEmpty())
        }
    }

    @Test
    fun dia_de23Horas_noDesplazaElSolape() {
        val zone = madrid
        val range = DateSpan(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 31))
        val wholeDay = timed("a", "2026-03-29T00:00", "2026-03-30T00:00", zone)

        val result = days(listOf(wholeDay), zone = zone, range = range)

        assertEquals(listOf("a"), result.on("2026-03-29").ids())
        assertTrue(result.on("2026-03-30").items.isEmpty())
        assertTrue(result.on("2026-03-28").items.isEmpty())
        assertFalse((result.on("2026-03-29").items.single() as DayItem.Event).continuesToNext)
    }

    @Test
    fun dia_de25Horas_noDesplazaElSolape() {
        val zone = madrid
        val range = DateSpan(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 27))
        val wholeDay = timed("a", "2026-10-25T00:00", "2026-10-26T00:00", zone)

        val result = days(listOf(wholeDay), zone = zone, range = range)

        assertEquals(listOf("a"), result.on("2026-10-25").ids())
        assertTrue(result.on("2026-10-26").items.isEmpty())
        assertTrue(result.on("2026-10-24").items.isEmpty())
    }

    @Test
    fun eventoConHora_cambiaDeDiaSegunLaZona() {
        val instant = Instant.parse("2026-11-20T23:30:00Z")
        val event = DeviceEvent("a", 1, 1, "Tarde", EventSpan.Timed(instant, instant.plusSeconds(1800)))

        assertEquals(listOf("a"), days(listOf(event), zone = utc).on("2026-11-20").ids())
        assertEquals(listOf("a"), days(listOf(event), zone = madrid).on("2026-11-21").ids())
    }

    @Test
    fun tarea_caeEnElDiaLocalDeSuDueAtConSuHora() {
        val task = TaskDue("t1", "Entregar", Instant.parse("2026-11-20T23:30:00Z"), place = "Proyecto")

        val inMadrid = days(tasks = listOf(task), zone = madrid).on("2026-11-21").items.single() as DayItem.Task
        val inUtc = days(tasks = listOf(task), zone = utc).on("2026-11-20").items.single() as DayItem.Task

        assertEquals(LocalTime.of(0, 30), inMadrid.time)
        assertEquals(LocalTime.of(23, 30), inUtc.time)
    }

    @Test
    fun tarea_fueraDelRango_noAparece() {
        val task = TaskDue("t1", "Lejos", Instant.parse("2026-12-20T10:00:00Z"), place = null)

        assertTrue(days(tasks = listOf(task)).all { it.items.isEmpty() })
    }

    @Test
    fun calendarioOculto_descartaSusEventos() {
        val events = listOf(
            timed("visible", "2026-11-20T10:00", "2026-11-20T11:00", calendarId = 1),
            timed("oculto", "2026-11-20T10:00", "2026-11-20T11:00", calendarId = 2),
        )

        val result = days(events, hidden = setOf(2L))

        assertEquals(listOf("visible"), result.on("2026-11-20").ids())
    }

    @Test
    fun calendarioDesconocido_muestraElEventoSinNombreNiColor() {
        val result = days(listOf(timed("a", "2026-11-20T10:00", "2026-11-20T11:00", calendarId = 99)))

        val item = result.on("2026-11-20").items.single() as DayItem.Event
        assertEquals(null, item.calendar)
    }

    @Test
    fun orden_todoElDiaPrimero_luegoHoraYEventoAntesQueTarea() {
        val events = listOf(
            timed("e-10", "2026-11-20T10:00", "2026-11-20T11:00", title = "Reunión"),
            timed("e-09", "2026-11-20T09:00", "2026-11-20T09:30", title = "Café"),
            allDay("d-personal", "2026-11-20", "2026-11-21", calendarId = 2, title = "Cumple"),
            allDay("d-trabajo", "2026-11-20", "2026-11-21", calendarId = 1, title = "Festivo"),
        )
        val tasks = listOf(
            TaskDue("t-10", "Aaa tarea", Instant.parse("2026-11-20T09:00:00Z"), null),
            TaskDue("t-08", "Temprana", Instant.parse("2026-11-20T07:00:00Z"), null),
        )

        val ids = days(events, tasks).on("2026-11-20").ids()

        assertEquals(listOf("d-personal", "d-trabajo", "t-t-08", "e-09", "e-10", "t-t-10"), ids)
    }

    @Test
    fun orden_aIgualHora_eventoAntesQueTareaYLuegoTitulo() {
        val events = listOf(
            timed("e-b", "2026-11-20T10:00", "2026-11-20T11:00", title = "bravo"),
            timed("e-a", "2026-11-20T10:00", "2026-11-20T11:00", title = "Alfa"),
        )
        val task = TaskDue("t1", "Aaa", Instant.parse("2026-11-20T09:00:00Z"), null)

        assertEquals(listOf("e-a", "e-b", "t-t1"), days(events, listOf(task)).on("2026-11-20").ids())
    }

    @Test
    fun orden_unEventoQueContinuaDesdeAyer_usaLas0000() {
        val events = listOf(
            timed("cruza", "2026-11-20T22:00", "2026-11-21T03:00", title = "Guardia"),
            timed("temprano", "2026-11-21T00:30", "2026-11-21T01:00", title = "Madrugón"),
        )

        assertEquals(listOf("cruza", "temprano"), days(events).on("2026-11-21").ids())
    }

    @Test
    fun includeEmpty_falso_omiteLosDiasSinElementos() {
        val result = days(listOf(timed("a", "2026-11-21T10:00", "2026-11-21T11:00")), includeEmpty = false)

        assertEquals(listOf(LocalDate.of(2026, 11, 21)), result.map { it.date })
    }

    @Test
    fun includeEmpty_verdadero_devuelveTodosLosDiasDelPeriodo() {
        assertEquals(span.days, days().map { it.date })
    }

    @Test
    fun dayMarks_cuentaEventosYTareas() {
        val events = listOf(
            timed("a", "2026-11-20T10:00", "2026-11-20T11:00"),
            allDay("b", "2026-11-20", "2026-11-21"),
        )
        val task = TaskDue("t1", "Tarea", Instant.parse("2026-11-20T09:00:00Z"), null)

        assertEquals(DayMarks(events = 2, tasks = 1), dayMarks(days(events, listOf(task)).on("2026-11-20")))
        assertEquals(DayMarks(events = 0, tasks = 0), dayMarks(days(events, listOf(task)).on("2026-11-21")))
    }
}
