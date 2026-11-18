package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.task.UNDO_WINDOW
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.tasks.TaskDeletionViewModel
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDeletionViewModelTest {

    private val repository = RecordingRepository()
    private val window = UNDO_WINDOW.toMillis()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun borrar_llamaAlRepositorioYPublicaElPendiente() = runTest {
        val viewModel = TaskDeletionViewModel(repository)

        viewModel.delete("t1")

        assertEquals(listOf("t1"), repository.deleted)
        assertEquals(listOf("t1"), viewModel.pending.value?.taskIds)
    }

    @Test
    fun borrar_conFallo_publicaElErrorYNoHayPendiente() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        repository.deleteFailure = failure
        val viewModel = TaskDeletionViewModel(repository)

        viewModel.delete("t1")

        assertEquals(failure, viewModel.error.value?.error)
        assertFalse(viewModel.error.value?.whileUndoing ?: true)
        assertNull(viewModel.pending.value)
    }

    @Test
    fun unNuevoBorrado_sustituyeAlPendiente() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")
        val first = viewModel.pending.value

        viewModel.delete("t2")

        assertEquals(listOf("t2"), viewModel.pending.value?.taskIds)
        assertNotEquals(first?.token, viewModel.pending.value?.token)
    }

    @Test
    fun elPendienteCaducaAlTerminarLaVentana() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        advanceTimeBy(window)
        testScheduler.runCurrent()

        assertNull(viewModel.pending.value)
    }

    @Test
    fun elPendienteSigueVivoJustoAntesDeLaVentana() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        advanceTimeBy(window - 1)

        assertNotNull(viewModel.pending.value)
    }

    @Test
    fun unNuevoBorrado_reiniciaLaVentana() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")
        advanceTimeBy(window - 1_000)
        viewModel.delete("t2")

        advanceTimeBy(window - 1)

        assertEquals(listOf("t2"), viewModel.pending.value?.taskIds)
        advanceTimeBy(1)
        testScheduler.runCurrent()
        assertNull(viewModel.pending.value)
    }

    @Test
    fun deshacer_restauraYLimpiaElPendiente() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        viewModel.undo()

        assertEquals(listOf(listOf("t1")), repository.restored)
        assertNull(viewModel.pending.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun deshacerDosVeces_noFalla() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        viewModel.undo()
        viewModel.undo()

        assertEquals(1, repository.restored.size)
    }

    @Test
    fun deshacer_trasCaducar_noRestaura() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")
        advanceTimeBy(window)
        testScheduler.runCurrent()

        viewModel.undo()

        assertTrue(repository.restored.isEmpty())
    }

    @Test
    fun restaurarFallido_publicaElErrorYLimpiaElPendiente() = runTest {
        val failure = DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Columna llena")
        repository.restoreFailure = failure
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        viewModel.undo()

        assertEquals(failure, viewModel.error.value?.error)
        assertTrue(viewModel.error.value?.whileUndoing ?: false)
        assertNull(viewModel.pending.value)
    }

    @Test
    fun consumeError_limpiaElError() = runTest {
        repository.deleteFailure = DomainError.NotFound("TASK_NOT_FOUND", "x")
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        viewModel.consumeError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun limpiar_publicaElLote() = runTest {
        repository.completedIds = listOf("c1", "c2", "c3")
        val viewModel = TaskDeletionViewModel(repository)

        val result = viewModel.deleteCompleted()

        assertTrue(result is DomainResult.Success)
        assertEquals(listOf("c1", "c2", "c3"), viewModel.pending.value?.taskIds)
    }

    @Test
    fun limpiar_sinCompletadas_noPublica() = runTest {
        val viewModel = TaskDeletionViewModel(repository)

        val result = viewModel.deleteCompleted()

        assertTrue(result is DomainResult.Success)
        assertNull(viewModel.pending.value)
    }

    @Test
    fun limpiar_conFallo_devuelveElErrorSinPendiente() = runTest {
        val failure = DomainError.Unknown("X", "fallo")
        repository.completedFailure = failure
        val viewModel = TaskDeletionViewModel(repository)

        val result = viewModel.deleteCompleted()

        assertEquals(failure, (result as DomainResult.Failure).error)
        assertNull(viewModel.pending.value)
    }

    @Test
    fun deshacerLote_restauraTodos() = runTest {
        repository.completedIds = listOf("c1", "c2")
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.deleteCompleted()

        viewModel.undo()

        assertEquals(listOf(listOf("c1", "c2")), repository.restored)
        assertNull(viewModel.pending.value)
    }

    @Test
    fun ventanaDeDeshacer_usaLaConstante() = runTest {
        val viewModel = TaskDeletionViewModel(repository)
        viewModel.delete("t1")

        advanceTimeBy(Duration.ofSeconds(3).toMillis() - 1)
        assertNotNull(viewModel.pending.value)
        advanceTimeBy(1)
        testScheduler.runCurrent()
        assertNull(viewModel.pending.value)
    }

    private class RecordingRepository(
        private val delegate: TaskRepository = FakeDestinationTaskRepository(),
    ) : TaskRepository by delegate {
        val deleted = mutableListOf<String>()
        val restored = mutableListOf<List<String>>()
        var deleteFailure: DomainError? = null
        var restoreFailure: DomainError? = null
        var completedFailure: DomainError? = null
        var completedIds: List<String> = emptyList()

        override suspend fun deleteTask(id: String): DomainResult<Unit> {
            deleteFailure?.let { return DomainResult.Failure(it) }
            deleted.add(id)
            return DomainResult.Success(Unit)
        }

        override suspend fun deleteCompleted(): DomainResult<List<String>> {
            completedFailure?.let { return DomainResult.Failure(it) }
            return DomainResult.Success(completedIds)
        }

        override suspend fun restoreTasks(ids: List<String>): DomainResult<Unit> {
            restoreFailure?.let { return DomainResult.Failure(it) }
            restored.add(ids)
            return DomainResult.Success(Unit)
        }
    }
}
