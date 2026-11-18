package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.PlannedRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.planned.PlannedItem
import com.simposio.taskeer.domain.planned.PlannedView
import com.simposio.taskeer.domain.planned.groupPlanned
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.planned.PlannedSectionKind
import com.simposio.taskeer.ui.planned.PlannedViewModel
import java.time.Instant
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
class PlannedViewModelTest {

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
    fun estado_exponeSeccionesEnOrden() = runTest {
        repository.set(
            item("lejos", "2026-11-25T09:00:00Z"),
            item("manana", "2026-11-18T09:00:00Z"),
            item("hoy", "2026-11-17T18:00:00Z"),
            item("ayer", "2026-11-16T09:00:00Z"),
        )

        val sections = viewModel().uiState.value.sections

        assertEquals(
            listOf(
                PlannedSectionKind.OVERDUE,
                PlannedSectionKind.TODAY,
                PlannedSectionKind.TOMORROW,
                PlannedSectionKind.UPCOMING,
            ),
            sections.map { it.kind },
        )
        assertEquals(listOf("ayer"), sections[0].items.map { it.id })
        assertEquals(listOf("lejos"), sections[3].days.single().items.map { it.id })
    }

    @Test
    fun seccionesVacias_noSeExponen() = runTest {
        repository.set(item("hoy", "2026-11-17T18:00:00Z"), item("lejos", "2026-11-25T09:00:00Z"))

        val kinds = viewModel().uiState.value.sections.map { it.kind }

        assertEquals(listOf(PlannedSectionKind.TODAY, PlannedSectionKind.UPCOMING), kinds)
    }

    @Test
    fun sinTareas_estadoVacio() = runTest {
        repository.set()

        assertTrue(viewModel().uiState.value.sections.isEmpty())
    }

    @Test
    fun fila_vencidaSeMarcaConIsOverdue() = runTest {
        repository.set(item("pasada-de-hoy", "2026-11-17T08:00:00Z"), item("futura-de-hoy", "2026-11-17T18:00:00Z"))

        val rows = viewModel().uiState.value.sections.single().items.associate { it.id to it.isOverdue }

        assertEquals(mapOf("pasada-de-hoy" to true, "futura-de-hoy" to false), rows)
    }

    @Test
    fun toggleCompleted_llamaASetCompleted() = runTest {
        val planned = item("a", "2026-11-17T18:00:00Z")
        repository.set(planned)
        tasks.inbox.value = listOf(planned.task)
        val viewModel = viewModel()

        viewModel.toggleCompleted("a")

        assertEquals(listOf("a" to true), tasks.completions)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun toggleCompleted_conFallo_exponeError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        tasks.completeFailure = failure
        repository.set(item("a", "2026-11-17T18:00:00Z"))
        val viewModel = viewModel()

        viewModel.toggleCompleted("a")

        assertEquals(failure, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.sections.isEmpty())
    }

    private fun TestScope.viewModel(): PlannedViewModel {
        val viewModel = PlannedViewModel(repository, tasks, converter) { now }
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
        private val view = MutableStateFlow(groupPlanned(emptyList(), dayWindowAt(now, zone), zone))

        fun set(vararg items: PlannedItem) {
            view.value = groupPlanned(items.toList(), dayWindowAt(now, zone), zone)
        }

        override fun observePlanned(): Flow<PlannedView> = view
    }
}
