package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.MyDayRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.MyDay
import com.simposio.taskeer.domain.model.MyDayTask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.myday.MyDaySort
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.myday.MyDayUiState
import com.simposio.taskeer.ui.myday.MyDayViewModel
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
import kotlinx.coroutines.test.advanceTimeBy
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
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)))))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isMarked)
    }

    @Test
    fun items_vacio_listaVaciaYTotalCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList())))

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0, viewModel.uiState.value.totalCount)
    }

    @Test
    fun tarjeta_exponeTableroPrioridadYFechaEnZonaDelUsuario() = runTest {
        val due = Instant.parse("2026-11-03T10:30:00Z")
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("a1", priority = TaskPriority.URGENT, dueAt = due, addedAt = now))),
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
                listOf(
                    item("pasada", dueAt = now.minusSeconds(1), addedAt = now),
                    item("igual", dueAt = now, addedAt = now),
                    item("futura", dueAt = now.plusSeconds(1), addedAt = now),
                ),
            ),
        )
        val viewModel = viewModel(repository)

        val overdue = viewModel.uiState.value.items.associate { it.id to it.isOverdue }

        assertEquals(mapOf("pasada" to true, "igual" to false, "futura" to false), overdue)
    }

    @Test
    fun tarjetaSinFecha_noTieneDueNiVencida() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now)))))

        val card = viewModel.uiState.value.items.single()

        assertNull(card.due)
        assertFalse(card.isOverdue)
    }

    @Test
    fun estadoInicial_ordenFechaLimite() = runTest {
        val state = viewModel(FakeMyDayRepository(MyDay(emptyList()))).uiState.value

        assertEquals(MyDaySort.DUE_DATE, state.sort)
    }

    @Test
    fun setSort_reordenaLaListaYConservaLaSeleccion() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(
                listOf(
                    item("pronto-baja", TaskPriority.LOW, dueAt = now.plusSeconds(1), addedAt = now),
                    item("tarde-urgente", TaskPriority.URGENT, dueAt = now.plusSeconds(99), addedAt = now),
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
    fun overdueCount_seExponeCompletoSinAbreviar() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList()))
        repository.overdue.value = 250
        val viewModel = viewModel(repository)

        assertEquals(250, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun overdueCount_seExponeConCero() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(emptyList())))

        assertEquals(0, viewModel.uiState.value.overdueCount)
    }

    @Test
    fun toggleMarked_marcada_delegaSetMarkedFalse() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p1" to false), repository.calls)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_soloMarcada_laTarjetaDesaparece() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now))),
        )
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(listOf("p2"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun toggleMarked_fallo_sePropagaAError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now))))
            .apply { this.failure = failure }
        val viewModel = viewModel(repository)

        viewModel.toggleMarked("p1")

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun toggleMarked_exito_limpiaElErrorPrevio() = runTest {
        val repository = FakeMyDayRepository(
            MyDay(listOf(item("p1", addedAt = now), item("p2", addedAt = now))),
        )
        val viewModel = viewModel(repository)
        repository.failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        viewModel.toggleMarked("p1")
        repository.failure = null

        viewModel.toggleMarked("p2")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun estado_soloExponeItemsOrdenConteosYError() {
        val properties = MyDayUiState::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") || it.name == "Companion" }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf("items", "sort", "overdueCount", "totalCount", "completedCount", "error", "loaded"),
            properties,
        )
    }

    @Test
    fun tareaDeBandeja_marcaIsInboxYSinNombreDeTablero() = runTest {
        val inbox = item("pan", addedAt = now).let { it.copy(task = it.task.copy(columnId = null), boardId = null, boardName = null) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(inbox))))

        val card = viewModel.uiState.value.items.single()

        assertTrue(card.isInbox)
        assertNull(card.boardName)
    }

    @Test
    fun tareaDeLista_muestraElNombreDeLaLista() = runTest {
        val shopping = item("leche", addedAt = now).let {
            it.copy(task = it.task.copy(columnId = null, listId = "compras"), boardId = null, boardName = null, listName = "Compras")
        }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(shopping))))

        val card = viewModel.uiState.value.items.single()

        assertEquals("Compras", card.listName)
        assertNull(card.boardName)
        assertTrue(card.isInbox)
        assertEquals("Compras", card.toRow().listName)
    }

    @Test
    fun tareaConTablero_noEsDeBandeja() = runTest {
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(item("t1", addedAt = now)))))

        val card = viewModel.uiState.value.items.single()

        assertFalse(card.isInbox)
        assertEquals("Tablero", card.boardName)
    }

    @Test
    fun tareaCreadaPorAltaRapida_apareceEnItems() = runTest {
        val repository = FakeMyDayRepository(MyDay(emptyList()))
        val viewModel = viewModel(repository)
        assertTrue(viewModel.uiState.value.items.isEmpty())

        val inbox = item("pan", addedAt = now).let { it.copy(task = it.task.copy(columnId = null), boardId = null, boardName = null) }
        repository.emit(MyDay(listOf(inbox)))

        assertEquals(listOf("pan"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun toggleCompleted_llamaASetCompleted() = runTest {
        val pending = item("a", addedAt = now)
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(pending.task) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pending))), tasks)

        viewModel.toggleCompleted("a")

        assertEquals(listOf("a" to true), tasks.completions)
    }

    @Test
    fun tareaCompletada_siguePresenteYNoCuentaComoVencida() = runTest {
        val overdue = item("a", dueAt = now.minusSeconds(60), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(overdue))).apply { this.overdue.value = 1 }
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(overdue.task) }
        val viewModel = viewModel(repository, tasks)
        assertEquals(1, viewModel.uiState.value.overdueCount)

        viewModel.toggleCompleted("a")
        val done = overdue.copy(task = overdue.task.copy(completedAt = now))
        repository.emit(MyDay(listOf(done)))
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
            MyDay(listOf(done, item("z-pendiente", dueAt = now.plusSeconds(600), addedAt = now))),
        )
        val viewModel = viewModel(repository)

        assertEquals(listOf("z-pendiente", "a-hecha"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun completadas_exponenIsCompletedYCompletedCount() = runTest {
        val done = item("hecha", addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val repository = FakeMyDayRepository(MyDay(listOf(done, item("pendiente", addedAt = now))))
        val viewModel = viewModel(repository)

        val state = viewModel.uiState.value

        assertEquals(mapOf("pendiente" to false, "hecha" to true), state.items.associate { it.id to it.isCompleted })
        assertEquals(1, state.completedCount)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun alCambiarElDia_laListaSeRecalculaSinAccionDelUsuario() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("ayer", addedAt = now))))
        val viewModel = viewModel(repository)
        assertEquals(listOf("ayer"), viewModel.uiState.value.items.map { it.id })

        repository.emit(MyDay(emptyList()))

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun tareaCompletada_nuncaSeMarcaVencida() = runTest {
        val done = item("a", dueAt = now.minusSeconds(60), addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(done))))

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
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pending))), tasks)

        viewModel.toggleCompleted("a")

        assertEquals(failure, viewModel.uiState.value.error)
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun manana_reprogramaYQuitaDeMiDia() = runTest {
        val pinned = item("a", dueAt = Instant.parse("2026-11-02T17:30:00Z"), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(pinned)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(pinned.task) }
        val viewModel = viewModel(repository, tasks)

        viewModel.reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(Instant.parse("2026-11-03T17:30:00Z"), tasks.updates.single().second.dueAt)
        assertEquals(listOf("a" to false), repository.calls)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun proximaSemana_reprogramaSinQuitarDeMiDia() = runTest {
        val pinned = item("a", addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(pinned)))
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
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(pinned))), tasks)

        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun onRowAction_quitarDeMiDia_desmarca() = runTest {
        val repository = FakeMyDayRepository(MyDay(listOf(item("p1", addedAt = now))))
        val viewModel = viewModel(repository)

        viewModel.onRowAction("p1", RowAction.REMOVE_FROM_MY_DAY)

        assertEquals(listOf("p1" to false), repository.calls)
    }

    @Test
    fun onRowAction_reabrir_completaALaInversa() = runTest {
        val done = item("a", addedAt = now).let { it.copy(task = it.task.copy(completedAt = now)) }
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(done.task) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(done))), tasks)

        viewModel.onRowAction("a", RowAction.REOPEN)

        assertEquals(listOf("a" to false), tasks.completions)
    }

    @Test
    fun completar_conservaLaPosicionHastaTerminarLaPausa() = runTest {
        val a = item("a", dueAt = now.plusSeconds(10), addedAt = now)
        val b = item("b", dueAt = now.plusSeconds(20), addedAt = now)
        val c = item("c", dueAt = now.plusSeconds(30), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(a, b, c)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(a.task, b.task, c.task) }
        val viewModel = viewModel(repository, tasks, settleMillis = 500L)

        viewModel.onRowAction("a", RowAction.COMPLETE)
        val done = a.copy(task = a.task.copy(completedAt = now))
        repository.emit(MyDay(listOf(done, b, c)))

        assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.items.map { it.id })
        assertTrue(viewModel.uiState.value.items.first().isCompleted)
        advanceTimeBy(499)
        assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.items.map { it.id })
        advanceTimeBy(1)
        testScheduler.runCurrent()
        assertEquals(listOf("b", "c", "a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun reabrir_noEspera() = runTest {
        val a = item("a", dueAt = now.plusSeconds(10), addedAt = now)
        val b = item("b", dueAt = now.plusSeconds(20), addedAt = now)
        val done = a.copy(task = a.task.copy(completedAt = now))
        val repository = FakeMyDayRepository(MyDay(listOf(done, b)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(done.task, b.task) }
        val viewModel = viewModel(repository, tasks, settleMillis = 500L)
        assertEquals(listOf("b", "a"), viewModel.uiState.value.items.map { it.id })

        viewModel.onRowAction("a", RowAction.REOPEN)
        repository.emit(MyDay(listOf(a, b)))

        assertEquals(listOf("a", "b"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun completar_sinAnimaciones_reordenaEnSeguida() = runTest {
        val a = item("a", dueAt = now.plusSeconds(10), addedAt = now)
        val b = item("b", dueAt = now.plusSeconds(20), addedAt = now)
        val repository = FakeMyDayRepository(MyDay(listOf(a, b)))
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(a.task, b.task) }
        val viewModel = viewModel(repository, tasks, settleMillis = 0L)

        viewModel.onRowAction("a", RowAction.COMPLETE)
        repository.emit(MyDay(listOf(a.copy(task = a.task.copy(completedAt = now)), b)))

        assertEquals(listOf("b", "a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun delete_noSeGestionaAqui() = runTest {
        val a = item("a", addedAt = now)
        val tasks = FakeDestinationTaskRepository().apply { inbox.value = listOf(a.task) }
        val viewModel = viewModel(FakeMyDayRepository(MyDay(listOf(a))), tasks)

        viewModel.onRowAction("a", RowAction.DELETE)

        assertTrue(tasks.completions.isEmpty())
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
    }

    private fun TestScope.viewModel(
        repository: FakeMyDayRepository,
        tasks: FakeDestinationTaskRepository = FakeDestinationTaskRepository(),
        settleMillis: Long = 0L,
    ): MyDayViewModel {
        val rescheduler = TaskRescheduler(tasks, madrid) { LocalDate.of(2026, 11, 2) }
        val viewModel = MyDayViewModel(repository, tasks, madrid, rescheduler, { settleMillis }) { now }
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

        override fun observeSuggestions(): Flow<List<MyDayTask>> = error("no se usa")

        override fun observeSuggestionCount(): Flow<Int> = error("no se usa")

        override suspend fun dismissSuggestion(taskId: String): DomainResult<Unit> = error("no se usa")

        override fun observeMarkedTaskIds(): Flow<Set<String>> = error("no se usa")

        override fun observeOverdueCount(): Flow<Int> = overdue

        override suspend fun setMarked(taskId: String, marked: Boolean): DomainResult<Unit> {
            calls += taskId to marked
            failure?.let { return DomainResult.Failure(it) }
            val current = myDay.value
            myDay.value = current.copy(pinned = current.pinned.filter { it.task.id != taskId })
            return DomainResult.Success(Unit)
        }
    }
}
