package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.FakeTaskListRepository
import com.simposio.taskeer.testing.listSummary
import com.simposio.taskeer.ui.tasks.MoveToListViewModel
import java.time.Instant
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoveToListViewModelTest {

    private val tasks = FakeDestinationTaskRepository()
    private val lists = FakeTaskListRepository(
        listOf(
            listSummary(SEEDED_TASK_LIST_ID, "Tareas", activeCount = 3),
            listSummary("compras", "Compras", activeCount = 2, position = 1),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tasks.inbox.value = listOf(task("pan"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun elegir_llamaAMoveToListYCierra() = runTest {
        val viewModel = viewModel()
        viewModel.open("pan", SEEDED_TASK_LIST_ID)
        assertTrue(viewModel.uiState.value.isOpen)

        viewModel.choose("compras")

        assertEquals(listOf("pan" to "compras"), tasks.listMoves)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun elegir_conFallo_exponeError() = runTest {
        tasks.moveFailure = DomainError.NotFound("LIST_NOT_FOUND", "La lista no existe")
        val viewModel = viewModel()
        viewModel.open("pan", SEEDED_TASK_LIST_ID)

        viewModel.choose("compras")

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals("LIST_NOT_FOUND", viewModel.uiState.value.error?.code)
    }

    @Test
    fun noOfreceLaListaActual() = runTest {
        val viewModel = viewModel()

        viewModel.open("pan", "compras")

        assertEquals(listOf(SEEDED_TASK_LIST_ID), viewModel.uiState.value.lists.map { it.list.id })
        viewModel.open("pan", SEEDED_TASK_LIST_ID)
        assertEquals(listOf("compras"), viewModel.uiState.value.lists.map { it.list.id })
    }

    @Test
    fun descartar_cierraSinMover() = runTest {
        val viewModel = viewModel()
        viewModel.open("pan", SEEDED_TASK_LIST_ID)

        viewModel.dismiss()

        assertFalse(viewModel.uiState.value.isOpen)
        assertTrue(tasks.listMoves.isEmpty())
    }

    private fun task(id: String) = Task(
        columnId = null,
        listId = SEEDED_TASK_LIST_ID,
        title = "Tarea $id",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )

    private fun TestScope.viewModel(): MoveToListViewModel {
        val viewModel = MoveToListViewModel(tasks, lists)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
