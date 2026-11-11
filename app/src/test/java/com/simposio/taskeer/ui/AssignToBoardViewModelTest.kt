package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.testing.FakeDestinationBoardRepository
import com.simposio.taskeer.testing.FakeDestinationColumnRepository
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.testing.testBoard
import com.simposio.taskeer.testing.testColumn
import com.simposio.taskeer.ui.tasks.AssignToBoardViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignToBoardViewModelTest {

    private val boards = FakeDestinationBoardRepository()
    private val columns = FakeDestinationColumnRepository()
    private val tasks = FakeDestinationTaskRepository()
    private lateinit var taskId: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        boards.boards.value = listOf(testBoard("b1"))
        columns.columns.value = listOf(
            testColumn("c1", "b1", position = 0),
            testColumn("hecho", "b1", position = 1, isDone = true),
        )
        val pan = (newTask(null, TaskDraft(title = "Pan"), 0, Instant.EPOCH) as DomainResult.Success).value
        tasks.inbox.value = listOf(pan)
        taskId = pan.id
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun open_cargaDestinosConCierreSinBandeja() = runTest {
        val viewModel = viewModel()

        viewModel.open(taskId)

        assertEquals(taskId, viewModel.uiState.value.taskId)
        assertEquals(listOf("c1", "hecho"), viewModel.uiState.value.destinations.single().columns.map { it.column.id })
    }

    @Test
    fun elegirColumna_asignaYCierraElSelector() = runTest {
        val viewModel = viewModel()
        viewModel.open(taskId)

        viewModel.choose("c1")

        assertEquals(listOf(taskId to "c1"), tasks.assignments)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun fallo_mantieneElSelectorAbiertoConElError() = runTest {
        val error = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        tasks.assignFailure = error
        val viewModel = viewModel()
        viewModel.open(taskId)

        viewModel.choose("c1")

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals(error, viewModel.uiState.value.error)
    }

    @Test
    fun columnaLlena_publicaColumnWipLimitReached() = runTest {
        tasks.assignFailure = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        val viewModel = viewModel()
        viewModel.open(taskId)

        viewModel.choose("c1")

        assertEquals("COLUMN_WIP_LIMIT_REACHED", viewModel.uiState.value.error?.code)
    }

    @Test
    fun dismiss_limpiaElEstado() = runTest {
        tasks.assignFailure = DomainError.NotFound("TASK_NOT_FOUND", "No existe")
        val viewModel = viewModel()
        viewModel.open(taskId)
        viewModel.choose("c1")

        viewModel.dismiss()

        assertNull(viewModel.uiState.value.taskId)
        assertNull(viewModel.uiState.value.error)
    }

    private fun TestScope.viewModel(): AssignToBoardViewModel {
        val viewModel = AssignToBoardViewModel(tasks, boards, columns)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}
