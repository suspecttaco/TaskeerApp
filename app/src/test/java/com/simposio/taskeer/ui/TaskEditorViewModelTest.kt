package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskDraft
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.newTask
import com.simposio.taskeer.ui.tasks.TaskEditorViewModel
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openForEdit_conTareaInexistente_noAbreElEditor() = runTest {
        val viewModel = viewModel(FakeTaskRepository())

        viewModel.openForEdit("no-existe")

        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun openForEdit_conTareaExistente_abreElEditorConSusDatos() = runTest {
        val viewModel = viewModel(FakeTaskRepository(task("t1", "Informe")))

        viewModel.openForEdit("t1")

        assertTrue(viewModel.uiState.value.isOpen)
        assertEquals("Informe", viewModel.uiState.value.title)
    }

    @Test
    fun delete_conBorradoEnCurso_noLanzaUnSegundoBorrado() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.delete()
        viewModel.delete()
        gate.complete(Unit)

        assertEquals(1, repository.deleteCalls)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    @Test
    fun delete_conFallo_muestraElErrorYPermiteReintentar() = runTest {
        val repository = FakeTaskRepository(task("t1", "Informe"))
        repository.deleteError = DomainError.Unknown("UNKNOWN", "Fallo")
        val viewModel = viewModel(repository)
        viewModel.openForEdit("t1")

        viewModel.delete()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun save_conTituloDe201_exponeElErrorDeLongitudYNoQuedaGuardando() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        viewModel.openForCreate("c1")
        viewModel.onTitleChange("x".repeat(201))

        viewModel.save()

        assertEquals("TASK_TITLE_TOO_LONG", viewModel.uiState.value.error?.code)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isOpen)
    }

    private fun viewModel(repository: TaskRepository) =
        TaskEditorViewModel(repository, DateTimeConverter { ZoneOffset.UTC })

    private fun task(id: String, title: String) = Task(
        columnId = "c1",
        title = title,
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        sync = SyncMetadata(clientId = id, updatedAt = Instant.EPOCH, pending = false, deletedAt = null),
    )

    private class FakeTaskRepository(vararg tasks: Task) : TaskRepository {
        private val stored = tasks.associateBy { it.id }
        var deleteGate: CompletableDeferred<Unit>? = null
        var deleteError: DomainError? = null
        var deleteCalls = 0

        override fun observeTasks(boardId: String): Flow<List<Task>> = emptyFlow()

        override suspend fun getTask(id: String): DomainResult<Task> =
            stored[id]?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(DomainError.NotFound("TASK_NOT_FOUND", "No existe"))

        override suspend fun createTask(columnId: String, draft: TaskDraft): DomainResult<Task> =
            newTask(columnId, draft, position = 0, now = Instant.EPOCH)

        override suspend fun updateTask(id: String, draft: TaskDraft): DomainResult<Task> =
            error("no se usa")

        override suspend fun moveTask(id: String, toColumnId: String, toIndex: Int): DomainResult<Task> =
            error("no se usa")

        override suspend fun deleteTask(id: String): DomainResult<Unit> {
            deleteCalls++
            deleteGate?.await()
            return deleteError?.let { DomainResult.Failure(it) } ?: DomainResult.Success(Unit)
        }
    }
}
