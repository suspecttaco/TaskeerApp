package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.AllTasksView
import com.simposio.taskeer.domain.planned.PlannedGroup
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.UpcomingDay
import com.simposio.taskeer.domain.planned.groupAllTasks
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.alltasks.AllTasksViewModel
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
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
class AllTasksViewModelTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private val now: Instant = Instant.parse("2026-11-17T10:00:00Z")
    private val today = MutableStateFlow(LocalDate.of(2026, 11, 17))
    private val converter = DateTimeConverter(TimeZoneProvider { zone })
    private val items = MutableStateFlow<List<PlannedItem>>(emptyList())
    private val tasks = FakeDestinationTaskRepository()

    private val repository = object : PlannedRepository {
        override fun observeUpcoming(): Flow<List<UpcomingDay>> = emptyFlow()

        override fun observeAllTasks(): Flow<AllTasksView> = items.map { groupAllTasks(it, today.value, zone) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_cuatroGruposEnOrden() = runTest {
        items.value = listOf(
            item("lejos", "2026-11-25T09:00:00Z"),
            item("sin", null),
            item("manana", "2026-11-18T09:00:00Z"),
            item("hoy", "2026-11-17T18:00:00Z"),
        )

        val groups = viewModel().uiState.value.groups

        assertEquals(
            listOf(PlannedGroup.TODAY, PlannedGroup.TOMORROW, PlannedGroup.UPCOMING, PlannedGroup.SOMEDAY),
            groups.map { it.group },
        )
        assertEquals(
            listOf(listOf("hoy"), listOf("manana"), listOf("lejos"), listOf("sin")),
            groups.map { group -> group.items.map { it.id } },
        )
    }

    @Test
    fun uiState_contadorPorGrupo() = runTest {
        items.value = listOf(
            item("a", "2026-11-17T18:00:00Z"),
            item("b", "2026-11-16T18:00:00Z"),
            item("c", null),
        )

        val counts = viewModel().uiState.value.groups.associate { it.group to it.count }

        assertEquals(
            mapOf(PlannedGroup.TODAY to 2, PlannedGroup.TOMORROW to 0, PlannedGroup.UPCOMING to 0, PlannedGroup.SOMEDAY to 1),
            counts,
        )
    }

    @Test
    fun uiState_sinTareas_exponeEstadoVacio() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(4, viewModel.uiState.value.groups.size)
        items.value = listOf(item("a", null))
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun uiState_algunDiaNoTieneHoraNiVencida() = runTest {
        items.value = listOf(item("sin", null))

        val row = viewModel().uiState.value.groups.last().items.single()

        assertEquals("", row.time)
        assertFalse(row.isOverdue)
    }

    @Test
    fun onRowAction_completar_llamaAlRepositorio() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        items.value = listOf(planned)
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
        items.value = listOf(item("a", "2026-11-17T18:00:00Z"))
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.COMPLETE)

        assertEquals(failure, viewModel.uiState.value.error)
    }

    @Test
    fun onRowAction_reprogramar_manana() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        items.value = listOf(planned)
        tasks.inbox.value = listOf(planned.task)

        viewModel().onRowAction("a", RowAction.RESCHEDULE_TOMORROW)

        assertEquals(Instant.parse("2026-11-18T18:00:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun onRowAction_reprogramar_sinFechaYElegirFecha() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        items.value = listOf(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.onRowAction("a", RowAction.RESCHEDULE_SOMEDAY)
        viewModel.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertNull(tasks.updates[0].second.dueAt)
        assertEquals(LocalDate.of(2026, 12, 1), tasks.updates[1].second.dueAt?.atZone(zone)?.toLocalDate())
    }

    @Test
    fun addDateFor_seEvaluaConElHoyActual() = runTest {
        val viewModel = viewModel()

        assertEquals(LocalDate.of(2026, 11, 18), viewModel.addDateFor(PlannedGroup.TOMORROW))
        today.value = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 19), viewModel.addDateFor(PlannedGroup.TOMORROW))
        assertEquals(LocalDate.of(2026, 11, 20), viewModel.addDateFor(PlannedGroup.UPCOMING))
        assertNull(viewModel.addDateFor(PlannedGroup.SOMEDAY))
    }

    private fun TestScope.viewModel(): AllTasksViewModel {
        val rescheduler = TaskRescheduler(tasks, converter) { LocalDate.of(2026, 11, 17) }
        val viewModel = AllTasksViewModel(repository, tasks, converter, rescheduler, { now }, { today.value })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun item(id: String, dueAt: String?): PlannedItem = PlannedItem(
        task = Task(
            columnId = "c1",
            title = "Tarea $id",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt?.let(Instant::parse),
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
