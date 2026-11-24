package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DayItem
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.calendar.TaskDue
import com.simposio.taskeer.domain.calendar.loadSpanFor
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.UpcomingDay
import com.simposio.taskeer.testing.FakeCalendarRepository
import com.simposio.taskeer.testing.FakeDayWindowSource
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.calendar.CalendarLoad
import com.simposio.taskeer.ui.calendar.CalendarUiState
import com.simposio.taskeer.ui.calendar.CalendarViewModel
import com.simposio.taskeer.ui.permission.CalendarAccess
import com.simposio.taskeer.ui.permission.CalendarNotice
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
class CalendarViewModelTest {

    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val friday = LocalDate.of(2026, 11, 20)

    private val work = DeviceCalendar(1, "Trabajo", "yo@example.com", 0xFF0000FF.toInt(), true)
    private val personal = DeviceCalendar(2, "Personal", "yo@example.com", 0xFF00FF00.toInt(), true)

    private var zone: ZoneId = utc
    private var firstDay = DayOfWeek.MONDAY
    private var readGranted = true
    private var writeGranted = true

    private val windows = FakeDayWindowSource(now, utc)
    private val preferences = FakePreferencesRepository()
    private val tasks = MutableStateFlow<List<TaskDue>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun event(
        key: String,
        start: String,
        end: String,
        calendarId: Long = 1,
        title: String = "Evento $key",
    ) = DeviceEvent(key, key.hashCode().toLong(), calendarId, title, EventSpan.Timed(Instant.parse(start), Instant.parse(end)))

    private fun task(id: String, dueAt: String) = TaskDue(id, "Tarea $id", Instant.parse(dueAt), place = null)

    private fun TestScope.viewModel(repository: FakeCalendarRepository): CalendarViewModel {
        val planned = object : PlannedRepository {
            override fun observeUpcoming(): Flow<List<UpcomingDay>> = emptyFlow()

            override fun observeAllTasks(): Flow<AllTasksView> = emptyFlow()

            override fun observeDatedTasks(): Flow<List<TaskDue>> = tasks
        }
        val viewModel = CalendarViewModel(
            calendarRepository = repository,
            plannedRepository = planned,
            preferences = preferences,
            dayWindowSource = windows,
            timeZone = { zone },
            firstDayOfWeek = { firstDay },
            isReadGranted = { readGranted },
            isWriteGranted = { writeGranted },
            clock = { now },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.canReadCalendars.collect {} }
        return viewModel
    }

    private fun repository(vararg events: DeviceEvent) =
        FakeCalendarRepository(calendars = listOf(work, personal), events = events.toList())

    private fun CalendarViewModel.state(): CalendarUiState = uiState.value

    private fun CalendarUiState.dayIds(date: LocalDate): List<String> =
        days.first { it.date == date }.items.map {
            when (it) {
                is DayItem.Event -> it.event.instanceKey
                is DayItem.Task -> it.task.taskId
            }
        }

    @Test
    fun estadoInicial_esAgendaEnElDiaDeHoyConRejillaDeSeisSemanas() = runTest {
        val state = viewModel(repository()).state()

        assertEquals(CalendarMode.AGENDA, state.mode)
        assertEquals(friday, state.anchor)
        assertEquals(friday, state.today)
        assertEquals(DayOfWeek.MONDAY, state.firstDay)
        assertEquals("UTC", state.zoneId)
        assertTrue(state.monthExpanded)
        assertEquals(6, state.grid.size)
        assertTrue(state.grid.all { it.size == 7 })
        assertEquals(CalendarLoad.Ready, state.load)
    }

    @Test
    fun sinLectura_muestraSoloTareasYNoConsultaElProveedor() = runTest {
        readGranted = false
        tasks.value = listOf(task("t1", "2026-11-20T09:00:00Z"))
        val repository = repository(event("e1", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z"))

        val state = viewModel(repository).state()

        assertEquals(CalendarAccess(read = false, write = true), state.access)
        assertEquals(CalendarNotice.ASK, state.readNotice)
        assertEquals(listOf("t1"), state.dayIds(friday))
        assertTrue(repository.eventRequests.isEmpty())
        assertEquals(0, repository.calendarSubscriptions)
        assertEquals(CalendarLoad.Ready, state.load)
    }

    @Test
    fun conLectura_noHayAvisoDePermisoYSeMuestranLosEventos() = runTest {
        val state = viewModel(repository(event("e1", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z"))).state()

        assertNull(state.readNotice)
        assertEquals(listOf("e1"), state.dayIds(friday))
    }

    @Test
    fun cargando_hastaElPrimerResultadoYLuegoListo() = runTest {
        val repository = FakeCalendarRepository(emitInitially = false)
        val viewModel = viewModel(repository)

        assertEquals(CalendarLoad.Loading, viewModel.state().load)

        repository.setCalendars(CalendarRead.Ok(listOf(work)))
        repository.setEvents(CalendarRead.Ok(emptyList()))

        assertEquals(CalendarLoad.Ready, viewModel.state().load)
    }

    @Test
    fun cargando_losEventosSiguenSinLlegar_mantieneLasTareasVisibles() = runTest {
        tasks.value = listOf(task("t1", "2026-11-20T09:00:00Z"))
        val viewModel = viewModel(FakeCalendarRepository(emitInitially = false))

        assertEquals(CalendarLoad.Loading, viewModel.state().load)
        assertEquals(listOf("t1"), viewModel.state().dayIds(friday))
    }

    @Test
    fun errorDeLectura_exponeElErrorYRetryVuelveAConsultar() = runTest {
        val repository = repository()
        val error = DomainError.Unknown("CALENDAR_UNAVAILABLE", "No se pudo leer el calendario")
        val viewModel = viewModel(repository)
        repository.setEvents(CalendarRead.Failed(error))

        assertEquals(CalendarLoad.Failed(error), viewModel.state().load)
        val requestsBefore = repository.eventRequests.size

        viewModel.retry()
        repository.setEvents(CalendarRead.Ok(emptyList()))

        assertEquals(requestsBefore + 1, repository.eventRequests.size)
        assertEquals(CalendarLoad.Ready, viewModel.state().load)
    }

    @Test
    fun listaDeCalendariosVacia_esNoCalendarsConLasTareasVisibles() = runTest {
        tasks.value = listOf(task("t1", "2026-11-20T09:00:00Z"))
        val repository = FakeCalendarRepository(calendars = emptyList())

        val state = viewModel(repository).state()

        assertEquals(CalendarLoad.NoCalendars, state.load)
        assertEquals(listOf("t1"), state.dayIds(friday))
    }

    @Test
    fun consulta_usaElRangoDeCargaDelModoYElPrimerDia() = runTest {
        val repository = repository()
        viewModel(repository)

        val (from, to) = repository.eventRequests.first()
        val expected = loadSpanFor(CalendarMode.AGENDA, friday, DayOfWeek.MONDAY)

        assertEquals(expected.start, from)
        assertEquals(expected.endExclusive, to)
    }

    @Test
    fun modos_cambianElNumeroDeDiasDelPeriodo() = runTest {
        val viewModel = viewModel(repository())

        viewModel.selectMode(CalendarMode.DAY)
        assertEquals(listOf(friday), viewModel.state().days.map { it.date })

        viewModel.selectMode(CalendarMode.THREE_DAYS)
        assertEquals(listOf(friday, friday.plusDays(1), friday.plusDays(2)), viewModel.state().days.map { it.date })

        viewModel.selectMode(CalendarMode.WEEK)
        assertEquals(LocalDate.of(2026, 11, 16), viewModel.state().days.first().date)
        assertEquals(7, viewModel.state().days.size)
    }

    @Test
    fun semana_empiezaEnElPrimerDiaDeLaSemanaDelSistema() = runTest {
        firstDay = DayOfWeek.SUNDAY
        val viewModel = viewModel(repository())

        viewModel.selectMode(CalendarMode.WEEK)

        assertEquals(LocalDate.of(2026, 11, 15), viewModel.state().days.first().date)
        assertEquals(DayOfWeek.SUNDAY, viewModel.state().grid.first().first().date.dayOfWeek)
    }

    @Test
    fun agenda_listaSoloLosDiasConElementos_yLosDemasModosTodos() = runTest {
        tasks.value = listOf(task("t1", "2026-11-25T09:00:00Z"))
        val viewModel = viewModel(repository(event("e1", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z")))

        assertEquals(listOf(friday, LocalDate.of(2026, 11, 25)), viewModel.state().days.map { it.date })

        viewModel.selectMode(CalendarMode.WEEK)

        assertEquals(7, viewModel.state().days.size)
        assertTrue(viewModel.state().days.any { it.items.isEmpty() })
    }

    @Test
    fun navegar_mueveElAnclaUnPeriodoYHoyVuelveAlDiaActual() = runTest {
        val viewModel = viewModel(repository())
        viewModel.selectMode(CalendarMode.DAY)

        viewModel.next()
        assertEquals(friday.plusDays(1), viewModel.state().anchor)
        viewModel.previous()
        viewModel.previous()
        assertEquals(friday.minusDays(1), viewModel.state().anchor)
        viewModel.goToToday()
        assertEquals(friday, viewModel.state().anchor)

        viewModel.selectMode(CalendarMode.WEEK)
        viewModel.next()
        assertEquals(friday.plusDays(7), viewModel.state().anchor)

        viewModel.selectMode(CalendarMode.AGENDA)
        viewModel.goToToday()
        viewModel.next()
        assertEquals(LocalDate.of(2026, 12, 20), viewModel.state().anchor)
    }

    @Test
    fun navegarDeMes_consultaElNuevoRango() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val requestsBefore = repository.eventRequests.size

        viewModel.next()

        assertEquals(requestsBefore + 1, repository.eventRequests.size)
        val expected = loadSpanFor(CalendarMode.AGENDA, LocalDate.of(2026, 12, 20), DayOfWeek.MONDAY)
        assertEquals(expected.start, repository.eventRequests.last().first)
    }

    @Test
    fun cambiarDeModoDentroDelMismoRango_noVuelveAConsultar() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val requestsBefore = repository.eventRequests.size

        viewModel.selectMode(CalendarMode.WEEK)

        assertEquals(requestsBefore, repository.eventRequests.size)
    }

    @Test
    fun selectDate_enModoPorDias_fijaAnclaYDestinoDeDesplazamiento() = runTest {
        val viewModel = viewModel(repository())
        viewModel.selectMode(CalendarMode.WEEK)

        viewModel.selectDate(LocalDate.of(2026, 11, 18))

        assertEquals(LocalDate.of(2026, 11, 18), viewModel.state().anchor)
        assertEquals(LocalDate.of(2026, 11, 18), viewModel.state().scrollTarget)
        assertTrue(viewModel.state().grid.flatten().single { it.isAnchor }.date == LocalDate.of(2026, 11, 18))

        viewModel.consumeScroll()

        assertNull(viewModel.state().scrollTarget)
    }

    @Test
    fun selectDate_enAgenda_vaAlPrimerDiaConElementosDesdeEsaFecha() = runTest {
        tasks.value = listOf(task("t1", "2026-11-25T09:00:00Z"))
        val viewModel = viewModel(repository())

        viewModel.selectDate(LocalDate.of(2026, 11, 22))

        assertEquals(LocalDate.of(2026, 11, 22), viewModel.state().anchor)
        assertEquals(LocalDate.of(2026, 11, 25), viewModel.state().scrollTarget)
    }

    @Test
    fun selectDate_enAgendaSinElementosPosteriores_noDejaUnDestinoPendiente() = runTest {
        tasks.value = listOf(task("t1", "2026-11-25T09:00:00Z"))
        val viewModel = viewModel(repository())

        viewModel.selectDate(LocalDate.of(2026, 11, 28))

        assertNull(viewModel.state().scrollTarget)
        tasks.value = listOf(task("t1", "2026-11-25T09:00:00Z"), task("t2", "2026-11-29T09:00:00Z"))
        assertNull(viewModel.state().scrollTarget)
    }

    @Test
    fun ocultarUnCalendario_filtraSusEventosYPersiste() = runTest {
        val viewModel = viewModel(
            repository(
                event("trabajo", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z", calendarId = 1),
                event("personal", "2026-11-20T12:00:00Z", "2026-11-20T13:00:00Z", calendarId = 2),
            ),
        )
        assertEquals(listOf("trabajo", "personal"), viewModel.state().dayIds(friday))

        viewModel.setCalendarVisible(1, visible = false)

        assertEquals(listOf("personal"), viewModel.state().dayIds(friday))
        assertEquals(setOf(1L), preferences.hiddenCalendarIds.first())
        assertEquals(listOf(false, true), viewModel.state().calendars.map { it.visible })

        viewModel.setCalendarVisible(1, visible = true)

        assertEquals(listOf("trabajo", "personal"), viewModel.state().dayIds(friday))
        assertEquals(emptySet<Long>(), preferences.hiddenCalendarIds.first())
    }

    @Test
    fun calendarioOcultoDeAntes_seAplicaAlAbrir() = runTest {
        preferences.setCalendarHidden(2, hidden = true)

        val viewModel = viewModel(
            repository(event("personal", "2026-11-20T12:00:00Z", "2026-11-20T13:00:00Z", calendarId = 2)),
        )

        assertTrue(viewModel.state().days.isEmpty())
    }

    @Test
    fun cambioDeZona_recalculaLosDiasYVuelveAConsultar() = runTest {
        val repository = repository(event("tarde", "2026-11-20T23:30:00Z", "2026-11-21T00:00:00Z"))
        val viewModel = viewModel(repository)
        assertEquals(listOf(friday), viewModel.state().days.map { it.date })
        val requestsBefore = repository.eventRequests.size

        zone = madrid
        windows.advanceTo(now, madrid)

        assertEquals("Europe/Madrid", viewModel.state().zoneId)
        assertEquals(listOf(LocalDate.of(2026, 11, 21)), viewModel.state().days.map { it.date })
        assertEquals(requestsBefore + 1, repository.eventRequests.size)
    }

    @Test
    fun alCruzarLaMedianoche_hoyYLaMarcaDeLaRejillaAvanzan() = runTest {
        val viewModel = viewModel(repository())
        assertEquals(friday, viewModel.state().grid.flatten().single { it.isToday }.date)

        windows.advanceTo(Instant.parse("2026-11-21T00:05:00Z"), utc)

        assertEquals(friday.plusDays(1), viewModel.state().today)
        assertEquals(friday.plusDays(1), viewModel.state().grid.flatten().single { it.isToday }.date)
        assertEquals(friday, viewModel.state().anchor)
    }

    @Test
    fun rejilla_marcaEventosTareasHoyYAncla() = runTest {
        tasks.value = listOf(task("t1", "2026-11-20T09:00:00Z"), task("t2", "2026-11-20T15:00:00Z"))
        val viewModel = viewModel(repository(event("e1", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z")))

        val cell = viewModel.state().grid.flatten().single { it.date == friday }

        assertEquals(1, cell.marks.events)
        assertEquals(2, cell.marks.tasks)
        assertTrue(cell.isToday && cell.isAnchor && cell.inMonth)
        assertFalse(viewModel.state().grid.flatten().first { it.date == LocalDate.of(2026, 10, 26) }.inMonth)
    }

    @Test
    fun toggleMonth_pliegaYDespliegaLaRejilla() = runTest {
        val viewModel = viewModel(repository())

        viewModel.toggleMonth()
        assertFalse(viewModel.state().monthExpanded)
        viewModel.toggleMonth()
        assertTrue(viewModel.state().monthExpanded)
    }

    @Test
    fun selectorDeCalendarios_seAbreYSeCierra() = runTest {
        val viewModel = viewModel(repository())

        viewModel.openPicker()
        assertTrue(viewModel.state().isPickerOpen)
        viewModel.closePicker()
        assertFalse(viewModel.state().isPickerOpen)
    }

    @Test
    fun permisoConcedido_trasLaRespuesta_cargaLosEventos() = runTest {
        readGranted = false
        val repository = repository(event("e1", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z"))
        val viewModel = viewModel(repository)
        assertTrue(viewModel.state().days.isEmpty())

        readGranted = true
        viewModel.onPermissionResult()

        assertNull(viewModel.state().readNotice)
        assertEquals(listOf("e1"), viewModel.state().dayIds(friday))
        assertEquals(1, repository.eventRequests.size)
    }

    @Test
    fun permisoDenegado_trasLaRespuesta_muestraDenegado() = runTest {
        readGranted = false
        val viewModel = viewModel(repository())

        viewModel.onPermissionResult()

        assertEquals(CalendarNotice.DENIED, viewModel.state().readNotice)
    }

    @Test
    fun alVolverDeLosAjustes_refreshAccessRelee() = runTest {
        readGranted = false
        val viewModel = viewModel(repository())
        viewModel.onPermissionResult()

        readGranted = true
        viewModel.refreshAccess()

        assertNull(viewModel.state().readNotice)
        assertTrue(viewModel.state().access.read)
    }

    @Test
    fun lecturaSinEscritura_exponeAmbosPermisosPorSeparado() = runTest {
        writeGranted = false

        assertEquals(CalendarAccess(read = true, write = false), viewModel(repository()).state().access)
    }

    @Test
    fun canReadCalendars_sigueAlPermisoDeLectura() = runTest {
        readGranted = false
        val viewModel = viewModel(repository())
        assertFalse(viewModel.canReadCalendars.value)

        readGranted = true
        viewModel.refreshAccess()

        assertTrue(viewModel.canReadCalendars.value)
    }

    @Test
    fun currentAnchor_devuelveElAnclaVigente() = runTest {
        val viewModel = viewModel(repository())
        viewModel.selectMode(CalendarMode.DAY)

        viewModel.next()

        assertEquals(friday.plusDays(1), viewModel.currentAnchor())
    }
}
