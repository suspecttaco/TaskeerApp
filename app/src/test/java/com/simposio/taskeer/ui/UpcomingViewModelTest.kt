package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.DayLabel
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.UpcomingDay
import com.simposio.taskeer.domain.planned.groupAllTasks
import com.simposio.taskeer.domain.planned.groupUpcoming
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import com.simposio.taskeer.ui.upcoming.UpcomingViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
class UpcomingViewModelTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private val now: Instant = Instant.parse("2026-11-17T10:00:00Z")
    private val converter = DateTimeConverter(TimeZoneProvider { zone })
    private val repository = FakePlannedRepository()
    private val tasks = FakeDestinationTaskRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_agrupaEnSieteDias() = runTest {
        repository.set(
            item("lejos", "2026-11-23T09:00:00Z"),
            item("manana", "2026-11-18T09:00:00Z"),
            item("hoy", "2026-11-17T18:00:00Z"),
        )

        val days = viewModel().uiState.value.days

        assertEquals(7, days.size)
        assertEquals(listOf("hoy"), days[0].items.map { it.id })
        assertEquals(listOf("manana"), days[1].items.map { it.id })
        assertEquals(listOf("lejos"), days[6].items.map { it.id })
        assertEquals(DayLabel.Today, days[0].label)
    }

    @Test
    fun uiState_marcaVencidasEnHoy() = runTest {
        repository.set(item("ayer", "2026-11-16T09:00:00Z"), item("futura-de-hoy", "2026-11-17T18:00:00Z"))

        val today = viewModel().uiState.value.days.first().items.associate { it.id to it.isOverdue }

        assertEquals(mapOf("ayer" to true, "futura-de-hoy" to false), today)
    }

    @Test
    fun uiState_expuestaElEstadoVacio() = runTest {
        repository.set()

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(7, viewModel.uiState.value.days.size)
        repository.set(item("hoy", "2026-11-17T18:00:00Z"))
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun onRowAction_completar_llamaAlRepositorio() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.COMPLETE)

        assertEquals(listOf("a" to true), tasks.completions)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun onRowAction_completar_conFallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        tasks.completeFailure = failure
        repository.set(item("a", "2026-11-17T18:00:00Z"))
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.COMPLETE)

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun onRowAction_reprogramar_manana() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.RESCHEDULE_TOMORROW)

        assertEquals(Instant.parse("2026-11-18T18:00:00Z"), tasks.updates.single().second.dueAt)
        assertTrue(tasks.completions.isEmpty())
    }

    @Test
    fun onRowAction_reprogramar_semanaQueViene() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.RESCHEDULE_NEXT_WEEK)

        assertEquals(Instant.parse("2026-11-24T18:00:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun onRowAction_reprogramar_sinFecha() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.RESCHEDULE_SOMEDAY)

        assertNull(tasks.updates.single().second.dueAt)
    }

    @Test
    fun onRowAction_reprogramar_elegirFecha() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(LocalDate.of(2026, 12, 1), tasks.updates.single().second.dueAt?.atZone(zone)?.toLocalDate())
    }

    @Test
    fun rescheduleTo_fallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        tasks.updateFailure = failure
        val viewModel = viewModel()

        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun reschedule_manana_reprograma() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)

        viewModel().reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(Instant.parse("2026-11-18T18:00:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun uiState_alCambiarElDia_cambianLasEtiquetas() = runTest {
        repository.set(item("t", "2026-11-18T09:00:00Z"))
        val viewModel = viewModel()
        assertEquals(DayLabel.Tomorrow, viewModel.uiState.value.days[1].label)

        repository.today.value = LocalDate.of(2026, 11, 18)

        val days = viewModel.uiState.value.days
        assertEquals(LocalDate.of(2026, 11, 18), days[0].date)
        assertEquals(DayLabel.Today, days[0].label)
        assertEquals(listOf("t"), days[0].items.map { it.id })
    }

    @Test
    fun uiState_tareaDeLista_muestraElNombreDeLaLista() = runTest {
        val shopping = item("leche", "2026-11-18T09:00:00Z").let {
            it.copy(task = it.task.copy(columnId = null, listId = "compras"), boardId = null, boardName = null, listName = "Compras")
        }
        repository.set(shopping)

        val row = viewModel().uiState.value.days[1].items.single()

        assertEquals("Compras", row.listName)
        assertNull(row.boardName)
    }

    @Test
    fun completar_laSacaDeLaVistaAlTerminarLaPausa() = runTest {
        val a = item("a", "2026-11-17T18:00:00Z")
        val b = item("b", "2026-11-17T19:00:00Z")
        repository.set(a, b)
        tasks.inbox.value = listOf(a.task, b.task)
        val viewModel = viewModel(settleMillis = 500L)

        viewModel.onRowAction("a", RowAction.COMPLETE)
        repository.set(b)

        val retained = viewModel.uiState.value.days[0].items
        assertEquals(listOf("a", "b"), retained.map { it.id })
        assertTrue(retained.first().isCompleted)
        advanceTimeBy(500)
        testScheduler.runCurrent()
        assertEquals(listOf("b"), viewModel.uiState.value.days[0].items.map { it.id })
    }

    @Test
    fun completar_sinAnimaciones_laSacaEnSeguida() = runTest {
        val a = item("a", "2026-11-17T18:00:00Z")
        repository.set(a)
        tasks.inbox.value = listOf(a.task)
        val viewModel = viewModel(settleMillis = 0L)

        viewModel.onRowAction("a", RowAction.COMPLETE)
        repository.set()

        assertTrue(viewModel.uiState.value.days[0].items.isEmpty())
    }

    @Test
    fun delete_noSeGestionaAqui() = runTest {
        val a = item("a", "2026-11-17T18:00:00Z")
        repository.set(a)
        tasks.inbox.value = listOf(a.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.DELETE)

        assertTrue(tasks.completions.isEmpty())
        assertEquals(listOf("a"), viewModel.uiState.value.days[0].items.map { it.id })
    }

    private fun TestScope.viewModel(settleMillis: Long = 0L): UpcomingViewModel {
        val rescheduler = TaskRescheduler(tasks, converter) { LocalDate.of(2026, 11, 17) }
        val viewModel = UpcomingViewModel(repository, tasks, converter, rescheduler, settleMillis = { settleMillis }) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

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

    private inner class FakePlannedRepository : PlannedRepository {
        val today = MutableStateFlow(LocalDate.of(2026, 11, 17))
        private val items = MutableStateFlow<List<PlannedItem>>(emptyList())

        fun set(vararg planned: PlannedItem) {
            items.value = planned.toList()
        }

        override fun observeUpcoming(): Flow<List<UpcomingDay>> =
            combine(today, items) { day, list -> groupUpcoming(list, day, zone) }

        override fun observeAllTasks(): Flow<AllTasksView> =
            combine(today, items) { day, list -> groupAllTasks(list, day, zone) }
    }
}
