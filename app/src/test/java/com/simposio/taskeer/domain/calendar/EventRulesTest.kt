package com.simposio.taskeer.domain.calendar

import com.simposio.taskeer.domain.model.DomainError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventRulesTest {

    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 11, 20)

    private val writable = DeviceCalendar(1, "Personal", "yo@example.com", 0xFF00FF00.toInt(), true)
    private val readOnly = DeviceCalendar(2, "Festivos", "yo@example.com", 0xFFFF0000.toInt(), false)

    private fun timedDraft(
        title: String = "Reunión",
        calendarId: Long? = 1,
        start: String = "2026-11-20T10:00:00Z",
        end: String = "2026-11-20T11:00:00Z",
    ) = EventDraft(title, calendarId, EventSpan.Timed(Instant.parse(start), Instant.parse(end)))

    private fun codeOf(draft: EventDraft, calendars: List<DeviceCalendar> = listOf(writable, readOnly)): String? =
        (validateEventDraft(draft, calendars) as DomainError.Validation?)?.code

    @Test
    fun validar_borradorCorrecto_noDaError() {
        assertNull(validateEventDraft(timedDraft(), listOf(writable)))
    }

    @Test
    fun validar_tituloVacioTrasTrim_pideTitulo() {
        assertEquals("EVENT_TITLE_REQUIRED", codeOf(timedDraft(title = "   ")))
    }

    @Test
    fun validar_tituloDe200_esValidoYDe201_esDemasiadoLargo() {
        assertNull(codeOf(timedDraft(title = "a".repeat(EVENT_TITLE_MAX))))
        assertEquals("EVENT_TITLE_TOO_LONG", codeOf(timedDraft(title = "a".repeat(EVENT_TITLE_MAX + 1))))
    }

    @Test
    fun validar_tituloLargoConEspacios_cuentaTrasTrim() {
        assertNull(codeOf(timedDraft(title = "  " + "a".repeat(EVENT_TITLE_MAX) + "  ")))
    }

    @Test
    fun validar_conHora_finIgualOAnteriorAlInicio_esError() {
        assertEquals(
            "EVENT_END_BEFORE_START",
            codeOf(timedDraft(start = "2026-11-20T10:00:00Z", end = "2026-11-20T10:00:00Z")),
        )
        assertEquals(
            "EVENT_END_BEFORE_START",
            codeOf(timedDraft(start = "2026-11-20T10:00:00Z", end = "2026-11-20T09:00:00Z")),
        )
    }

    @Test
    fun validar_todoElDia_finExclusivoNoPosteriorAlInicio_esError() {
        val sameDay = EventDraft("Festivo", 1, EventSpan.AllDay(day, day.plusDays(1)))
        val inverted = EventDraft("Festivo", 1, EventSpan.AllDay(day, day))

        assertNull(codeOf(sameDay))
        assertEquals("EVENT_END_BEFORE_START", codeOf(inverted))
    }

    @Test
    fun validar_sinCalendario_pideCalendario() {
        assertEquals("EVENT_CALENDAR_REQUIRED", codeOf(timedDraft(calendarId = null)))
    }

    @Test
    fun validar_calendarioDeSoloLecturaOInexistente_noEsEscribible() {
        assertEquals("EVENT_CALENDAR_NOT_WRITABLE", codeOf(timedDraft(calendarId = 2)))
        assertEquals("EVENT_CALENDAR_NOT_WRITABLE", codeOf(timedDraft(calendarId = 99)))
    }

    @Test
    fun validar_variosErrores_devuelveElPrimeroEnElOrdenDelSpec() {
        val draft = timedDraft(title = "", calendarId = null, start = "2026-11-20T10:00:00Z", end = "2026-11-20T09:00:00Z")

        assertEquals("EVENT_TITLE_REQUIRED", codeOf(draft))
    }

    @Test
    fun validar_loscodigosSonErroresDeValidacion() {
        assertEquals(DomainError.Validation::class, validateEventDraft(timedDraft(title = ""), emptyList())!!::class)
    }

    @Test
    fun borradorPorDefecto_hoy_empiezaEnLaProximaHoraEnPunto() {
        val draft = defaultEventDraft(day, Instant.parse("2026-11-20T10:20:00Z"), utc, calendarId = 1)

        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T11:00:00Z"), Instant.parse("2026-11-20T12:00:00Z")),
            draft.span,
        )
        assertEquals(1L, draft.calendarId)
        assertEquals("", draft.title)
    }

    @Test
    fun borradorPorDefecto_hoyEnPunto_saltaALaHoraSiguiente() {
        val draft = defaultEventDraft(day, Instant.parse("2026-11-20T10:00:00Z"), utc, calendarId = null)

        assertEquals(Instant.parse("2026-11-20T11:00:00Z"), (draft.span as EventSpan.Timed).start)
    }

    @Test
    fun borradorPorDefecto_hoyUsaLaZonaVigente() {
        val draft = defaultEventDraft(day, Instant.parse("2026-11-20T10:20:00Z"), madrid, calendarId = null)

        assertEquals(
            LocalDateTime.of(2026, 11, 20, 12, 0),
            (draft.span as EventSpan.Timed).start.atZone(madrid).toLocalDateTime(),
        )
    }

    @Test
    fun borradorPorDefecto_otroDia_empiezaALas0900ConUnaHora() {
        val anchor = LocalDate.of(2026, 11, 25)

        val draft = defaultEventDraft(anchor, Instant.parse("2026-11-20T10:20:00Z"), utc, calendarId = null)

        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-25T09:00:00Z"), Instant.parse("2026-11-25T10:00:00Z")),
            draft.span,
        )
    }

    @Test
    fun borradorPorDefecto_pasadaLas2300_laHoraSiguienteEsMedianocheDelDiaSiguiente() {
        val draft = defaultEventDraft(day, Instant.parse("2026-11-20T23:30:00Z"), utc, calendarId = null)

        assertEquals(Instant.parse("2026-11-21T00:00:00Z"), (draft.span as EventSpan.Timed).start)
    }

    @Test
    fun withAllDay_desdeUnBorradorConHora_tomaLasFechasLocales() {
        val draft = defaultEventDraft(day, Instant.parse("2026-11-20T10:20:00Z"), utc, calendarId = 1)

        val allDay = withAllDay(draft, allDay = true, zone = utc)

        assertEquals(EventSpan.AllDay(day, day.plusDays(1)), allDay.span)
    }

    @Test
    fun withAllDay_conFinEnOtroDia_incluyeEseDia() {
        val draft = timedDraft(start = "2026-11-20T18:00:00Z", end = "2026-11-22T08:00:00Z")

        assertEquals(EventSpan.AllDay(day, LocalDate.of(2026, 11, 23)), withAllDay(draft, true, utc).span)
    }

    @Test
    fun withAllDay_volverAHora_usaLasHorasPorDefectoDelDiaDeInicio() {
        val allDay = EventDraft("Festivo", 1, EventSpan.AllDay(day, day.plusDays(2)))

        val timed = withAllDay(allDay, allDay = false, zone = utc)

        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T09:00:00Z"), Instant.parse("2026-11-20T10:00:00Z")),
            timed.span,
        )
    }

    @Test
    fun withStart_conHora_conservaLaDuracion() {
        val draft = timedDraft(start = "2026-11-20T10:00:00Z", end = "2026-11-20T11:30:00Z")

        val moved = withStart(draft, LocalDateTime.of(2026, 11, 21, 14, 0), utc)

        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-21T14:00:00Z"), Instant.parse("2026-11-21T15:30:00Z")),
            moved.span,
        )
    }

    @Test
    fun withStart_cruzandoElCambioDeHora_conservaLaDuracionReal() {
        val start = LocalDateTime.of(2026, 3, 29, 0, 0).atZone(madrid).toInstant()
        val draft = EventDraft("Vuelo", 1, EventSpan.Timed(start, start.plus(Duration.ofHours(2))))

        val moved = withStart(draft, LocalDateTime.of(2026, 3, 29, 0, 30), madrid)

        val span = moved.span as EventSpan.Timed
        assertEquals(Duration.ofHours(2), Duration.between(span.start, span.end))
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), span.end.atZone(madrid).toLocalDateTime())
    }

    @Test
    fun withStart_todoElDia_conservaElNumeroDeDias() {
        val draft = EventDraft("Viaje", 1, EventSpan.AllDay(day, day.plusDays(3)))

        val moved = withStart(draft, LocalDateTime.of(2026, 11, 25, 0, 0), utc)

        assertEquals(EventSpan.AllDay(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 28)), moved.span)
    }

    @Test
    fun withEnd_anteriorAlInicio_noSeCorrigeEnSilencio() {
        val draft = timedDraft(start = "2026-11-20T10:00:00Z", end = "2026-11-20T11:00:00Z")

        val moved = withEnd(draft, LocalDateTime.of(2026, 11, 20, 9, 0), utc)

        assertEquals("EVENT_END_BEFORE_START", codeOf(moved))
    }

    @Test
    fun withEnd_todoElDia_elDiaElegidoEsElUltimoIncluido() {
        val draft = EventDraft("Viaje", 1, EventSpan.AllDay(day, day.plusDays(1)))

        val moved = withEnd(draft, LocalDateTime.of(2026, 11, 23, 0, 0), utc)

        assertEquals(EventSpan.AllDay(day, LocalDate.of(2026, 11, 24)), moved.span)
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), eventEndLocal(moved, utc))
    }

    @Test
    fun eventStartLocal_yEventStartDate_usanLaZonaVigente() {
        val draft = timedDraft(start = "2026-11-20T23:30:00Z", end = "2026-11-21T00:30:00Z")

        assertEquals(LocalDateTime.of(2026, 11, 21, 0, 30), eventStartLocal(draft, madrid))
        assertEquals(LocalDate.of(2026, 11, 21), eventStartDate(draft, madrid))
        assertEquals(day, eventStartDate(draft, utc))
    }

    @Test
    fun defaultCalendarId_elPrimeroEscribibleYVisible() {
        val a = DeviceCalendar(10, "Zeta", "a@x.com", 0, true)
        val b = DeviceCalendar(11, "Alfa", "b@x.com", 0, true)
        val ro = DeviceCalendar(12, "Aaa", "a@x.com", 0, false)

        assertEquals(10L, defaultCalendarId(listOf(b, ro, a), emptySet()))
    }

    @Test
    fun defaultCalendarId_saltaLosOcultosYSinCandidatosDaNulo() {
        val a = DeviceCalendar(10, "Zeta", "a@x.com", 0, true)
        val b = DeviceCalendar(11, "Alfa", "b@x.com", 0, true)

        assertEquals(11L, defaultCalendarId(listOf(a, b), hiddenIds = setOf(10L)))
        assertNull(defaultCalendarId(listOf(a, b), hiddenIds = setOf(10L, 11L)))
        assertNull(defaultCalendarId(listOf(DeviceCalendar(1, "Solo lectura", "a@x.com", 0, false)), emptySet()))
    }
}
