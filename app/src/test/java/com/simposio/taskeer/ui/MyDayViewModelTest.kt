package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.myday.MyDayFilter
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.myday.MyDayUiState
import com.simposio.taskeer.ui.myday.MyDayViewModel
import com.simposio.taskeer.ui.tasks.TASK_REPEAT_NOT_RESCHEDULABLE
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import com.simposio.taskeer.ui.tasks.toRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class MyDayViewModelTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val madrid = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun items_soloMarcada_unElementoMarcado() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isMarked)
        assertFalse(card.isAssigned)
    }

    @Test
    fun items_soloAsignada_unElementoAsignado() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), listOf(item("a1", dueAt = now)))))

        val card = viewModel.uiState.value.items.single()

        assertFalse(card.isMarked)
        assertTrue(card.isAssigned)
    }

    @Test
    fun items_marcadaYAsignada_unSoloElementoConAmbosOrigenes() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("t1", addedAt = now)), listOf(item("t1", dueAt = now.plusSeconds(5)))),
        )
        val viewModel = viewModel(repository)

        val state = viewModel.uiState.value

        assertEquals(1, state.items.size)
        assertTrue(state.items.single().isMarked && state.items.single().isAssigned)
        assertEquals(1, state.totalCount)
    }

    @Test
    fun items_vacio_listaVaciaYTotalCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList())))

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0, viewModel.uiState.value.totalCount)
    }

    @Test
    fun tarjeta_exponeTableroPrioridadYFechaEnZonaDelUsuario() = runTest {
        val due = Instant.parse("2026-11-03T10:30:00Z")
        val repository = FakeMyDayRepository(
            MyDay(emptyList(), listOf(item("a1", priority = TaskPriority.URGENT, dueAt = due))),
        )
        val viewModel = viewModel(repository)

        val card = viewModel.uiState.value.items.single()

        assertEquals("Tablero", card.boardName)
        assertEquals(TaskPriority.URGENT, card.priority)
        assertTrue(card.due.orEmpty().contains("11:30"))
    }

    @Test
    fun tarjeta_isOverdue_dependeDelRelojInyectado() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(
                    item("pasada", dueAt = now.minusSeconds(1)),
                    item("igual", dueAt = now),
                    item("futura", dueAt = now.plusSeconds(1)),
                ),
            ),
        )
        val viewModel = viewModel(repository)

        val overdue = viewModel.uiState.value.items.associate { it.id to it.isOverdue }

        assertEquals(mapOf("pasada" to true, "igual" to false, "futura" to false), overdue)
    }

    @Test
    fun tarjetaSinFecha_noTieneDueNiVencida() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertNull(card.due)
        assertFalse(card.isOverdue)
    }

    @Test
    fun estadoInicial_ordenFechaLimiteYFiltroTodas() = runTest {
        val state = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList()))).uiState.value

        assertEquals(MyDaySort.DUE_DATE, state.sort)
        assertEquals(MyDayFilter.ALL, state.filter)
    }

    @Test
    fun setSort_reordenaLaListaYConservaLaSeleccion() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(
                    item("pronto-baja", TaskPriority.LOW, dueAt = now.plusSeconds(1)),
                    item("tarde-urgente", TaskPriority.URGENT, dueAt = now.plusSeconds(99)),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        assertEquals(listOf("pronto-baja", "tarde-urgente"), viewModel.uiState.value.items.map { it.id })

        viewModel.setSort(MyDaySort.PRIORITY)

        assertEquals(MyDaySort.PRIORITY, viewModel.uiState.value.sort)
        assertEquals(listOf("tarde-urgente", "pronto-baja"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun setFilter_muestraSoloLosQueLoCumplenYTotalNoCambia() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                pinned = listOf(item("m", addedAt = now)),
                assigned = listOf(item("a", dueAt = now.plusSeconds(10))),
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.setFilter(MyDayFilter.MARKED)
        assertEquals(listOf("m"), viewModel.uiState.value.items.map { it.id })
        viewModel.setFilter(MyDayFilter.ASSIGNED)

        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
        assertEquals(2, viewModel.uiState.value.totalCount)
    }

    @Test
    fun setFilter_overdue_listaCoincideConElConteo() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                emptyList(),
                listOf(item("v1", dueAt = now.minusSeconds(5)), item("v2", dueAt = now.minusSeconds(9)), item("f", dueAt = now.plusSeconds(9))),
            ),
        )
        repository.overdue.value = 2
        val viewModel = viewModel(repository)

        viewModel.setFilter(MyDayFilter.OVERDUE)

        assertEquals(viewModel.uiState.value.overdueCount, viewModel.uiState.value.items.size)
    }

    @Test
    fun overdueCount_seExponeCompletoSinAbreviar() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList()))
        repository.overdue.value = 250
        val viewModel = viewModel(repository)

        assertEquals(250, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun overdueCount_seExponeConCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList())))

        assertEquals(0, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun toggleMarked_marcada_delegaSetMarkedFalse() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList()))
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p1" to false), repository.calls)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_noMarcada_delegaSetMarkedTrueYLaHaceMarcada() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), listOf(item("a1", dueAt = now.plusSeconds(5)))))
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("a1")

        assertEquals(listOf("a1" to true), repository.calls)
        assertTrue(viewModel.uiState.value.items.single().isMarked)
    }

    @Test
    fun toggleMarked_soloMarcada_laTarjetaDesaparece() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now)), emptyList()),
        )
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p2"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun toggleMarked_marcadaYAsignada_permaneceSinOrigenMarcada() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("t1", addedAt = now)), listOf(item("t1", dueAt = now.plusSeconds(5)))),
        )
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("t1")

        val card = viewModel.uiState.value.items.single()
        assertFalse(card.isMarked)
        assertTrue(card.isAssigned)
    }

    @Test
    fun toggleMarked_fallo_sePropagaAError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList()))
            .apply { this.failure = failure }
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_exito_limpiaElErrorPrevio() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now)), emptyList()),
        )
        val viewModel = viewModel(repository)
        repository.failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        viewModel.toggleMarked("p1")
        repository.failure = null

        viewModel.toggleMarked("p2")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun estado_soloExponeItemsOrdenFiltroConteosYError() {
        val properties = MyDayUiState::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") || it.name == "Companion" }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "items", "sort", "filter", "overdueCount", "totalCount", "completedCount",
                "suggestions", "suggestionsExpanded", "error",
            ),
            properties,
        )
    }

    @Test
    fun tareaDeBandeja_marcaIsInboxYSinNombreDeTablero() = runTest {
        val inbox = item("pan", addedAt = now).let { it.copy(task = it.task.copy(columnId = null), boardId = null, boardName = null) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(inbox), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isInbox)
        assertNull(card.boardName)
    }

    @Test
    fun tareaConTablero_noEsDeBandeja() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("t1", addedAt = now)), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertFalse(card.isInbox)
        assertEquals("Tablero", card.boardName)
    }

    @Test
    fun tareaCreadaPorAltaRapida_apareceEnItems() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList()))
        val viewModel = viewModel(repository)
        assertTrue(viewModel.uiState.value.items.isEmpty())

        val inbox = item("pan", addedAt = now).let { it.copy(task = it.task.copy(columnId = null), boardId = null, boardName = null) }
        repository.emit(MyDay(listOf(inbox), emptyList()))

        assertEquals(listOf("pan"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun toggleCompleted_llamaASetCompleted() = runTest {
        val pending = item("a", addedAt = now)
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(pending.task) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pending), emptyList())), tasks)

        viewModel.toggleCompleted("a")

        assertEquals(listOf("a" to true), tasks.completions)
    }

    @Test
    fun tareaCompletada_siguePresenteYNoCuentaComoVencida() = runTest {
        val overdue = item("a", dueAt = now.minusSeconds(60), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(overdue), listOf(overdue))).apply { this.overdue.value = 1 }
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(overdue.task) }
        val viewModel = viewModel(repository, tasks)
        assertEquals(1, viewModel.uiState.value.overdueCount)

        viewModel.toggleCompleted("a")
        val done = overdue.copy(task = overdue.task.copy(completedAt = now))
        repository.emit(MyDay(listOf(done), listOf(done)))
        repository.overdue.value = 0

        val card = viewModel.uiState.value.items.single()
        assertTrue(card.isCompleted)
        assertFalse(card.isOverdue)
        assertEquals(0, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun completadas_vanDespuesDeLasPendientes() = runTest {
        val done = item("a-hecha", dueAt = now.minusSeconds(600), addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val repository = FakeMyDayRepository(
            MyDay(listOf(done, item("z-pendiente", dueAt = now.plusSeconds(600), addedAt = now)), emptyList()),
        )
        val viewModel = viewModel(repository)

        assertEquals(listOf("z-pendiente", "a-hecha"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun completadas_exponenIsCompletedYCompletedCount() = runTest {
        val done = item("hecha", addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val repository = FakeMyDayRepository(MyDay(listOf(done, item("pendiente", addedAt = now)), emptyList()))
        val viewModel = viewModel(repository)

        val state = viewModel.uiState.value

        assertEquals(mapOf("pendiente" to false, "hecha" to true), state.items.associate { it.id to it.isCompleted })
        assertEquals(1, state.completedCount)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun alCambiarElDia_laListaSeRecalculaSinAccionDelUsuario() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("ayer", addedAt = now)), emptyList()))
        val viewModel = viewModel(repository)
        assertEquals(listOf("ayer"), viewModel.uiState.value.items.map { it.id })

        repository.emit(MyDay(emptyList(), emptyList(), suggestions = listOf(item("ayer", addedAt = now))))

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(listOf("ayer"), viewModel.uiState.value.suggestions.map { it.id })
    }

    @Test
    fun addSuggestion_llamaASetMarkedTrue() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.addSuggestion("s1")

        assertEquals(listOf("s1" to true), repository.calls)
        assertEquals(listOf("s1"), viewModel.uiState.value.items.map { it.id })
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun dismissSuggestion_llamaASetMarkedFalse() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.dismissSuggestion("s1")

        assertEquals(listOf("s1" to false), repository.calls)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun suggestion_conFallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))
            .apply { this.failure = failure }
        val viewModel = viewModel(repository)

        viewModel.addSuggestion("s1")

        assertEquals(failure, viewModel.uiState.value.error)
        assertEquals(listOf("s1"), viewModel.uiState.value.suggestions.map { it.id })
    }

    @Test
    fun suggestions_seExponenEnElEstado() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))

        val suggestion = viewModel(repository).uiState.value.suggestions.single()

        assertEquals("s1", suggestion.id)
        assertEquals("Tarea s1", suggestion.title)
        assertEquals("Tablero", suggestion.boardName)
    }

    @Test
    fun suggestions_sinMarcasAnteriores_estanVacias() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList())))

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun toggleSuggestions_cambiaSuggestionsExpanded() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList())))
        assertTrue(viewModel.uiState.value.suggestionsExpanded)

        viewModel.toggleSuggestions()
        assertFalse(viewModel.uiState.value.suggestionsExpanded)

        viewModel.toggleSuggestions()
        assertTrue(viewModel.uiState.value.suggestionsExpanded)
    }

    @Test
    fun tareaCompletada_nuncaSeMarcaVencida() = runTest {
        val done = item("a", dueAt = now.minusSeconds(60), addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(done), emptyList())))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isCompleted)
        assertFalse(card.isOverdue)
    }

    @Test
    fun toggleCompleted_conFallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val pending = item("a", addedAt = now)
        val tasks = FakeDestinationTaskRepository().apply {
            inbox.value = listOf(pending.task)
            completeFailure = failure
        }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pending), emptyList())), tasks)

        viewModel.toggleCompleted("a")

        assertEquals(failure, viewModel.uiState.value.error)
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun manana_reprogramaYQuitaDeMiDia() = runTest {
        val pinned = item("a", dueAt = Instant.parse("2026-11-02T17:30:00Z"), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(pinned), emptyList()))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(pinned.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(Instant.parse("2026-11-03T17:30:00Z"), tasks.updates.single().second.dueAt)
        assertEquals(listOf("a" to false), repository.calls)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun manana_tareaSoloAsignada_soloReprograma() = runTest {
        val assigned = item("a", dueAt = Instant.parse("2026-11-02T17:30:00Z"))
        val repository = FakeMyDayRepository(MyDay(emptyList(), listOf(assigned)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(assigned.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(1, tasks.updates.size)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun proximaSemana_reprogramaSinQuitarDeMiDia() = runTest {
        val pinned = item("a", addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(pinned), emptyList()))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(pinned.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.reschedule("a", RescheduleTarget.NEXT_WEEK)

        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), tasks.updates.single().second.dueAt)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun rescheduleTo_fallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val pinned = item("a", addedAt = now)
        val tasks = FakeDestinationTaskRepository().apply {
            inbox.value = listOf(pinned.task)
            updateFailure = failure
        }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pinned), emptyList())), tasks)

        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun sugerencia_mantenerHoy_marca() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.addSuggestion("s1")

        assertEquals(listOf("s1" to true), repository.calls)
        assertEquals(listOf("s1"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun sugerencia_descartar_desmarca() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.dismissSuggestion("s1")

        assertEquals(listOf("s1" to false), repository.calls)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun sugerencia_completar_laResuelveYDejaDeSugerirse() = runTest {
        val suggested = item("s1", addedAt = now)
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(suggested)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(suggested.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.completeSuggestion("s1")
        val done = suggested.copy(task = suggested.task.copy(completedAt = now))
        repository.emit(MyDay(emptyList(), emptyList(), listOf(done)))

        assertEquals(listOf("s1" to true), tasks.completions)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun sugerencia_manana_reprogramaYDescarta() = runTest {
        val suggested = item("s1", dueAt = Instant.parse("2026-11-01T17:30:00Z"), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(suggested)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(suggested.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.suggestionTomorrow("s1")

        assertEquals(Instant.parse("2026-11-03T17:30:00Z"), tasks.updates.single().second.dueAt)
        assertEquals(listOf("s1" to false), repository.calls)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun sugerencia_conRepeticion_noOfreceManana() = runTest {
        val repeating = item("s1", dueAt = Instant.parse("2026-11-01T17:30:00Z"), addedAt = now)
            .let { it.copy(task = it.task.copy(repeat = TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 1))) }
        val repository = FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(repeating)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(repeating.task) }
        val viewModel = viewModel(repository, tasks)
        val row = viewModel.uiState.value.suggestions.single().toRow()

        viewModel.suggestionTomorrow("s1")

        assertTrue(row.isRepeating)
        assertFalse(RowAction.RESCHEDULE_TOMORROW in row.actionsIn(RowContext.SUGGESTION).sheet)
        assertTrue(tasks.updates.isEmpty())
        assertTrue(repository.calls.isEmpty())
        assertEquals(TASK_REPEAT_NOT_RESCHEDULABLE, viewModel.uiState.value.error?.code)
    }

    @Test
    fun sugerencia_exponePrioridadYFecha() = runTest {
        val suggested = item("s1", priority = TaskPriority.URGENT, dueAt = now.minusSeconds(60), addedAt = now)
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList(), emptyList(), listOf(suggested))))

        val suggestion = viewModel.uiState.value.suggestions.single()

        assertEquals(TaskPriority.URGENT, suggestion.priority)
        assertTrue(suggestion.isOverdue)
        assertEquals(false, suggestion.due.isNullOrBlank())
    }

    @Test
    fun onRowAction_quitarDeMiDia_desmarca() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)), emptyList()))
        val viewModel = viewModel(repository)

        viewModel.onRowAction(RowContext.MY_DAY, "p1", RowAction.REMOVE_FROM_MY_DAY)

        assertEquals(listOf("p1" to false), repository.calls)
    }

    @Test
    fun onRowAction_reabrir_completaALaInversa() = runTest {
        val done = item("a", addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(done.task) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(done), emptyList())), tasks)

        viewModel.onRowAction(RowContext.MY_DAY, "a", RowAction.REOPEN)

        assertEquals(listOf("a" to false), tasks.completions)
    }

    @Test
    fun onRowAction_sugerenciaMantener_marcaYDescartarDesmarca() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(emptyList(), emptyList(), listOf(item("s1", addedAt = now), item("s2", addedAt = now))),
        )
        val viewModel = viewModel(repository)

        viewModel.onRowAction(RowContext.SUGGESTION, "s1", RowAction.KEEP_TODAY)
        viewModel.onRowAction(RowContext.SUGGESTION, "s2", RowAction.DISMISS)

        assertEquals(listOf("s1" to true, "s2" to false), repository.calls)
    }

    private fun TestScope.viewModel(
        repository: FakeMyDayRepository,
        tasks: FakeDestinationTaskRepository = FakeDestinationTaskRepository(),
    ): MyDayViewModel {
        val rescheduler = TaskRescheduler(tasks, madrid) { LocalDate.of(2026, 11, 2) }
        val viewModel = MyDayViewModel(repository, tasks, madrid, rescheduler) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun item(
        id: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Instant? = null,
        addedAt: Instant? = null,
    ): MyDayTask = MyDayTask(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = priority,
            dueAt = dueAt,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
        ),
        boardId = "b1",
        boardName = "Tablero",
        addedAt = addedAt,
    )

    private class FakeMyDayRepository(initial: MyDay) : MyDayRepository {
        private val myDay = MutableStateFlow(initial)
        val overdue = MutableStateFlow(0)
        val calls: MutableList<Pair<String, Boolean>> = mutableListOf()
        var failure: DomainError? = null

        override fun observeMyDay(): Flow<MyDay> = myDay

        fun emit(value: MyDay) {
            myDay.value = value
        }

        override fun observeMarkedTaskIds(): Flow<Set<String>> = error("no se usa")

        override fun observeOverdueCount(): Flow<Int> = overdue

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            failure?.let { return DomainResult.Failure(it) }
            val current = myDay.value
            val known = (current.pinned + current.assigned + current.suggestions).first { it.task.id == taskId }
            val pinned = current.pinned.filter { it.task.id != taskId }
            myDay.value = current.copy(
                pinned = if (marked) pinned + known.copy(addedAt = Instant.EPOCH) else pinned,
                suggestions = current.suggestions.filter { it.task.id != taskId },
            )
            return DomainResult.Success(Unit)
        }
    }
}
