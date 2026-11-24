package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.testing.FakeCalendarRepository
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.calendar.EventEditorUiState
import com.simposio.taskeer.ui.calendar.EventEditorViewModel
import com.simposio.taskeer.ui.calendar.EventPanel
import com.simposio.taskeer.ui.permission.CalendarAccess
import com.simposio.taskeer.ui.permission.CalendarNotice
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorViewModelTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-20T10:20:00Z")
    private val friday = LocalDate.of(2026, 11, 20)

    private val work = DeviceCalendar(1, "Trabajo", "a@example.com", 0xFF0000FF.toInt(), true)
    private val holidays = DeviceCalendar(2, "Festivos", "a@example.com", 0xFFFF0000.toInt(), false)
    private val personal = DeviceCalendar(3, "Personal", "b@example.com", 0xFF00FF00.toInt(), true)

    private var readGranted = true
    private var writeGranted = true
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(repository: FakeCalendarRepository): EventEditorViewModel {
        val viewModel = EventEditorViewModel(
            calendarRepository = repository,
            preferences = preferences,
            isReadGranted = { readGranted },
            isWriteGranted = { writeGranted },
            timeZone = { utc },
            clock = { now },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun repository(vararg calendars: DeviceCalendar) = FakeCalendarRepository(calendars = calendars.toList())

    private fun EventEditorViewModel.state(): EventEditorUiState = uiState.value

    private fun EventEditorViewModel.openValid() {
        open(friday)
        onTitleChange("Reunión")
    }

    @Test
    fun open_iniciaEnLaProximaHoraDelDiaDelAnclaConUnaHoraDeDuracion() = runTest {
        val viewModel = viewModel(repository(work, holidays))

        viewModel.open(friday)

        val state = viewModel.state()
        assertTrue(state.isOpen)
        assertEquals(EventPanel.Form, state.panel)
        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T11:00:00Z"), Instant.parse("2026-11-20T12:00:00Z")),
            state.draft.span,
        )
        assertEquals("", state.draft.title)
    }

    @Test
    fun open_otroDia_empiezaALas0900() = runTest {
        val viewModel = viewModel(repository(work))

        viewModel.open(LocalDate.of(2026, 11, 25))

        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-25T09:00:00Z"), Instant.parse("2026-11-25T10:00:00Z")),
            viewModel.state().draft.span,
        )
    }

    @Test
    fun open_eligeElPrimerCalendarioEscribibleYVisible() = runTest {
        preferences.setCalendarHidden(1, hidden = true)
        val viewModel = viewModel(repository(work, holidays, personal))

        viewModel.open(friday)

        assertEquals(3L, viewModel.state().draft.calendarId)
        assertEquals(listOf(1L, 2L, 3L), viewModel.state().calendars.map { it.id })
    }

    @Test
    fun open_sinTitulo_noSePuedeGuardarYAvisaDelTitulo() = runTest {
        val viewModel = viewModel(repository(work))

        viewModel.open(friday)

        assertEquals("EVENT_TITLE_REQUIRED", viewModel.state().validation?.code)
        assertFalse(viewModel.state().canSave)
        assertFalse(viewModel.state().titleTouched)
    }

    @Test
    fun titulo_valido_habilitaGuardarYMarcaElCampoComoTocado() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)

        viewModel.onTitleChange("Reunión")

        assertNull(viewModel.state().validation)
        assertTrue(viewModel.state().canSave)
        assertTrue(viewModel.state().titleTouched)
    }

    @Test
    fun todoElDia_alternaEntreFechasYHoras() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)

        viewModel.onAllDayChange(true)
        assertEquals(EventSpan.AllDay(friday, friday.plusDays(1)), viewModel.state().draft.span)

        viewModel.onAllDayChange(false)
        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T09:00:00Z"), Instant.parse("2026-11-20T10:00:00Z")),
            viewModel.state().draft.span,
        )
    }

    @Test
    fun paneles_atrasVuelveAlFormularioYDesdeElFormularioCierra() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)

        viewModel.openPanel(EventPanel.StartDate)
        assertEquals(EventPanel.StartDate, viewModel.state().panel)
        viewModel.onBack()
        assertEquals(EventPanel.Form, viewModel.state().panel)
        assertFalse(viewModel.state().isClosing)

        viewModel.onBack()
        assertTrue(viewModel.state().isClosing)
    }

    @Test
    fun cambiarElInicio_conservaLaDuracionYVuelveAlFormulario() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)
        viewModel.openPanel(EventPanel.StartDate)

        viewModel.onPickStartDate(LocalDate.of(2026, 11, 22))
        viewModel.onPickStartTime(LocalTime.of(14, 30))

        assertEquals(EventPanel.Form, viewModel.state().panel)
        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-22T14:30:00Z"), Instant.parse("2026-11-22T15:30:00Z")),
            viewModel.state().draft.span,
        )
    }

    @Test
    fun finAnteriorAlInicio_seMuestraElErrorYNoSeCorrigeEnSilencio() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.openValid()

        viewModel.onPickEndTime(LocalTime.of(10, 0))

        assertEquals("EVENT_END_BEFORE_START", viewModel.state().validation?.code)
        assertFalse(viewModel.state().canSave)
        assertEquals(Instant.parse("2026-11-20T10:00:00Z"), (viewModel.state().draft.span as EventSpan.Timed).end)
    }

    @Test
    fun finDeTodoElDia_elDiaElegidoEsElUltimoIncluido() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)
        viewModel.onAllDayChange(true)

        viewModel.onPickEndDate(LocalDate.of(2026, 11, 22))

        assertEquals(EventSpan.AllDay(friday, LocalDate.of(2026, 11, 23)), viewModel.state().draft.span)
    }

    @Test
    fun guardarValido_llamaACreateEventConElBorradorYCierraConLaFechaCreada() = runTest {
        val repository = repository(work)
        val viewModel = viewModel(repository)
        viewModel.openValid()

        viewModel.save()

        val draft = repository.created.single()
        assertEquals("Reunión", draft.title)
        assertEquals(1L, draft.calendarId)
        assertEquals(
            EventSpan.Timed(Instant.parse("2026-11-20T11:00:00Z"), Instant.parse("2026-11-20T12:00:00Z")),
            draft.span,
        )
        assertTrue(viewModel.state().isClosing)
        assertFalse(viewModel.state().isSaving)
        assertEquals(friday, viewModel.state().createdDate)
    }

    @Test
    fun guardarEnOtroDia_lacreadaApuntaAlDiaDeInicio() = runTest {
        val repository = repository(work)
        val viewModel = viewModel(repository)
        viewModel.open(LocalDate.of(2026, 11, 25))
        viewModel.onTitleChange("Viaje")

        viewModel.save()

        assertEquals(LocalDate.of(2026, 11, 25), viewModel.state().createdDate)
    }

    @Test
    fun consumirLaFechaCreada_laLimpiaYDismissNoLaPierdeAntes() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.openValid()
        viewModel.save()

        viewModel.dismiss()
        assertFalse(viewModel.state().isOpen)
        assertEquals(friday, viewModel.state().createdDate)

        viewModel.consumeCreated()
        assertNull(viewModel.state().createdDate)
    }

    @Test
    fun guardarInvalido_noLlamaACreateEvent() = runTest {
        val repository = repository(work)
        val viewModel = viewModel(repository)
        viewModel.open(friday)

        viewModel.save()

        assertTrue(repository.created.isEmpty())
        assertTrue(viewModel.state().isOpen)
    }

    @Test
    fun guardarConFallo_conservaElBorradorExponeElErrorYPermiteReintentar() = runTest {
        val repository = repository(work)
        repository.createResult =
            DomainResult.Failure(DomainError.Unknown("CALENDAR_WRITE_FAILED", "No se pudo guardar el evento"))
        val viewModel = viewModel(repository)
        viewModel.openValid()

        viewModel.save()

        assertTrue(viewModel.state().isOpen)
        assertFalse(viewModel.state().isClosing)
        assertEquals("CALENDAR_WRITE_FAILED", viewModel.state().error?.code)
        assertEquals("Reunión", viewModel.state().draft.title)
        assertTrue(viewModel.state().canSave)
        assertNull(viewModel.state().createdDate)

        repository.createResult = DomainResult.Success(5L)
        viewModel.save()

        assertEquals(2, repository.created.size)
        assertNull(viewModel.state().error)
        assertTrue(viewModel.state().isClosing)
    }

    @Test
    fun editarTrasUnFallo_limpiaElErrorDeGuardado() = runTest {
        val repository = repository(work)
        repository.createResult =
            DomainResult.Failure(DomainError.Forbidden("CALENDAR_PERMISSION_DENIED", "Sin permiso"))
        val viewModel = viewModel(repository)
        viewModel.openValid()
        viewModel.save()
        assertEquals("CALENDAR_PERMISSION_DENIED", viewModel.state().error?.code)

        viewModel.onTitleChange("Reunión 2")

        assertNull(viewModel.state().error)
    }

    @Test
    fun sinCalendarioEscribible_muestraElMotivoYNoPermiteGuardar() = runTest {
        val repository = repository(holidays)
        val viewModel = viewModel(repository)
        viewModel.openValid()

        assertFalse(viewModel.state().hasWritableCalendar)
        assertFalse(viewModel.state().canSave)
        assertNull(viewModel.state().draft.calendarId)

        viewModel.save()

        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun elegirUnCalendarioDeSoloLectura_noSePuedeGuardar() = runTest {
        val viewModel = viewModel(repository(work, holidays))
        viewModel.openValid()
        viewModel.openPanel(EventPanel.Calendars)

        viewModel.onCalendarChosen(2)

        assertEquals(EventPanel.Form, viewModel.state().panel)
        assertEquals("EVENT_CALENDAR_NOT_WRITABLE", viewModel.state().validation?.code)
        assertFalse(viewModel.state().canSave)

        viewModel.onCalendarChosen(1)

        assertTrue(viewModel.state().canSave)
    }

    @Test
    fun sinEscritura_bloqueaElGuardadoYPideElPermiso() = runTest {
        writeGranted = false
        val repository = repository(work)
        val viewModel = viewModel(repository)
        viewModel.openValid()

        assertEquals(CalendarAccess(read = true, write = false), viewModel.state().access)
        assertEquals(CalendarNotice.ASK, viewModel.state().writeNotice)
        assertFalse(viewModel.state().canSave)

        viewModel.save()

        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun sinEscritura_trasDenegar_pasaADenegadoYAlConcederSeHabilitaElFormulario() = runTest {
        readGranted = false
        writeGranted = false
        val repository = repository(work)
        val viewModel = viewModel(repository)
        viewModel.openValid()
        assertTrue(viewModel.state().calendars.isEmpty())

        viewModel.onPermissionResult()
        assertEquals(CalendarNotice.DENIED, viewModel.state().writeNotice)

        readGranted = true
        writeGranted = true
        viewModel.onPermissionResult()

        assertNull(viewModel.state().writeNotice)
        assertEquals(listOf(1L), viewModel.state().calendars.map { it.id })
        assertEquals(1L, viewModel.state().draft.calendarId)
        assertTrue(viewModel.state().canSave)
    }

    @Test
    fun alVolverDeLosAjustes_refreshAccessActualizaElPermiso() = runTest {
        writeGranted = false
        val viewModel = viewModel(repository(work))
        viewModel.open(friday)

        writeGranted = true
        viewModel.refreshAccess()

        assertTrue(viewModel.state().access.write)
        assertNull(viewModel.state().writeNotice)
    }

    @Test
    fun cerrar_descartaElBorradorSinConfirmar() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.openValid()

        viewModel.close()
        assertTrue(viewModel.state().isClosing)
        viewModel.dismiss()

        assertFalse(viewModel.state().isOpen)
        assertEquals("", viewModel.state().draft.title)
        assertEquals(EventPanel.Form, viewModel.state().panel)
    }

    @Test
    fun reabrir_empiezaDeCeroSinArrastrarElBorradorAnterior() = runTest {
        val viewModel = viewModel(repository(work))
        viewModel.openValid()
        viewModel.dismiss()

        viewModel.open(friday)

        assertEquals("", viewModel.state().draft.title)
        assertFalse(viewModel.state().titleTouched)
        assertNull(viewModel.state().error)
    }

    @Test
    fun cerrarSinHojaAbierta_noHaceNada() = runTest {
        val viewModel = viewModel(repository(work))

        viewModel.close()

        assertFalse(viewModel.state().isClosing)
    }
}
