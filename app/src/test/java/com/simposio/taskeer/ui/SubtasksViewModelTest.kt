package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.SubtaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SubtaskDraft
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.ui.tasks.SubtasksUiState
import com.simposio.taskeer.ui.tasks.SubtasksViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtasksViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun open_cargaLasSubtareasDeLaTarea() = runTest {
        val repository = FakeSubtaskRepository(subtask("s1", "t1"), subtask("s2", "t2"))
        val viewModel = SubtasksViewModel(repository)

        viewModel.open("t1")

        assertEquals("t1", viewModel.uiState.value.taskId)
        assertEquals(listOf("s1"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun add_creaYVaciaElCampo() = runTest {
        val repository = FakeSubtaskRepository()
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")
        viewModel.onNewTitleChange("Nueva")

        viewModel.add()

        assertEquals(listOf("Nueva"), repository.created)
        assertEquals("", viewModel.uiState.value.newTitle)
        assertEquals(listOf("Nueva"), viewModel.uiState.value.items.map { it.title })
    }

    @Test
    fun add_conTituloEnBlanco_noLlamaAlRepositorio() = runTest {
        val repository = FakeSubtaskRepository()
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")
        viewModel.onNewTitleChange("   ")

        viewModel.add()

        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun add_conFallo_publicaElErrorYConservaElCampo() = runTest {
        val repository = FakeSubtaskRepository().apply {
            failWith = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        }
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")
        viewModel.onNewTitleChange("Nueva")

        viewModel.add()

        assertEquals("TASK_NOT_FOUND", viewModel.uiState.value.error?.code)
        assertEquals("Nueva", viewModel.uiState.value.newTitle)
    }

    @Test
    fun saveEdit_actualizaElTituloYCierraLaEdicion() = runTest {
        val repository = FakeSubtaskRepository(subtask("s1", "t1", "Viejo"))
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")
        viewModel.startEdit("s1")
        viewModel.onEditTitleChange("Nuevo")

        viewModel.saveEdit()

        assertEquals(listOf("Nuevo"), viewModel.uiState.value.items.map { it.title })
        assertNull(viewModel.uiState.value.editingId)
    }

    @Test
    fun toggle_delegaEnSetSubtaskCompleted() = runTest {
        val repository = FakeSubtaskRepository(subtask("s1", "t1"))
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")

        viewModel.toggle("s1", true)

        assertTrue(viewModel.uiState.value.items.single().completed)
    }

    @Test
    fun delete_delegaEnElRepositorio() = runTest {
        val repository = FakeSubtaskRepository(subtask("s1", "t1"))
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")

        viewModel.delete("s1")

        assertEquals(listOf("s1"), repository.deleted)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun close_liberaElEstado() = runTest {
        val repository = FakeSubtaskRepository(subtask("s1", "t1"))
        val viewModel = SubtasksViewModel(repository)
        viewModel.open("t1")
        viewModel.onNewTitleChange("pendiente")

        viewModel.close()

        assertEquals(SubtasksUiState(), viewModel.uiState.value)
    }

    private fun subtask(id: String, taskId: String, title: String = "Subtarea $id"): Subtask =
        Subtask(taskId, title, false, 0, SyncMetadata(id, Instant.EPOCH, pending = true, deletedAt = null))

    private class FakeSubtaskRepository(vararg initial: Subtask) : SubtaskRepository {
        private val rows = MutableStateFlow(initial.toList())

        val created: MutableList<String> = mutableListOf()
        val deleted: MutableList<String> = mutableListOf()
        var failWith: DomainError? = null

        override fun observeSubtasks(taskId: String): Flow<List<Subtask>> =
            rows.map { all -> all.filter { it.taskId == taskId } }

        override suspend fun createSubtask(taskId: String, draft: SubtaskDraft): DomainResult<Subtask> {
            failWith?.let { return DomainResult.Failure(it) }
            created += draft.title
            val subtask = Subtask(
                taskId, draft.title, false, rows.value.size,
                SyncMetadata("n${rows.value.size}", Instant.EPOCH, pending = true, deletedAt = null),
            )
            rows.value = rows.value + subtask
            return DomainResult.Success(subtask)
        }

        override suspend fun updateSubtask(id: String, draft: SubtaskDraft): DomainResult<Subtask> =
            change(id) { it.copy(title = draft.title) }

        override suspend fun setSubtaskCompleted(id: String, completed: Boolean): DomainResult<Subtask> =
            change(id) { it.copy(completed = completed) }

        override suspend fun deleteSubtask(id: String): DomainResult<Unit> {
            deleted += id
            rows.value = rows.value.filterNot { it.id == id }
            return DomainResult.Success(Unit)
        }

        private fun change(id: String, transform: (Subtask) -> Subtask): DomainResult<Subtask> {
            val current = rows.value.first { it.id == id }
            val updated = transform(current)
            rows.value = rows.value.map { if (it.id == id) updated else it }
            return DomainResult.Success(updated)
        }
    }
}
