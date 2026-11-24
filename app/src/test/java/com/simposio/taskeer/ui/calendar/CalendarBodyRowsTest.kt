package com.simposio.taskeer.ui.calendar

import com.simposio.taskeer.domain.calendar.CalendarDay
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.DayItem
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.calendar.TaskDue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarBodyRowsTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val tomorrow = today.plusDays(1)

    private val event = DayItem.Event(
        DeviceEvent("7:1000", 7, 1, "Reunión", EventSpan.AllDay(today, tomorrow.plusDays(1))),
        calendar = null,
        continuesFromPrevious = false,
        continuesToNext = false,
    )
    private val task = DayItem.Task(TaskDue("t1", "Entregar", Instant.EPOCH, null), LocalTime.NOON)

    @Test
    fun cadaDiaLlevaSuCabeceraYSusFilas() {
        val rows = bodyRows(listOf(CalendarDay(today, listOf(event, task))), CalendarMode.WEEK, today)

        assertEquals(listOf("d:2026-11-20", "e:7:1000:2026-11-20", "t:t1:2026-11-20"), rows.map { it.key })
        assertTrue((rows.first() as BodyRow.DayHeader).isToday)
    }

    @Test
    fun unEventoMultidia_tieneUnaClaveDistintaPorDia() {
        val rows = bodyRows(
            listOf(CalendarDay(today, listOf(event)), CalendarDay(tomorrow, listOf(event))),
            CalendarMode.WEEK,
            today,
        )

        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun diaSinNada_enModoPorDias_muestraSuFilaVacia() {
        val rows = bodyRows(listOf(CalendarDay(tomorrow, emptyList())), CalendarMode.DAY, today)

        assertEquals(listOf("d:2026-11-21", "x:2026-11-21"), rows.map { it.key })
        assertTrue(!(rows.first() as BodyRow.DayHeader).isToday)
    }

    @Test
    fun agendaSinNadaEnElMes_muestraSoloElTextoDeMesVacio() {
        assertEquals(listOf(BodyRow.EmptyMonth), bodyRows(emptyList(), CalendarMode.AGENDA, today))
    }

    @Test
    fun modoPorDiasSinDias_noMuestraTextoDeMes() {
        assertTrue(bodyRows(emptyList(), CalendarMode.WEEK, today).isEmpty())
    }

    @Test
    fun indexOfDay_devuelveLaPosicionDeLaCabecera() {
        val rows = bodyRows(
            listOf(CalendarDay(today, listOf(event, task)), CalendarDay(tomorrow, listOf(task))),
            CalendarMode.AGENDA,
            today,
        )

        assertEquals(0, rows.indexOfDay(today))
        assertEquals(3, rows.indexOfDay(tomorrow))
        assertNull(rows.indexOfDay(today.plusDays(5)))
    }
}
