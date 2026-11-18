package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.TaskRepository
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.shell.ClearCompletedViewModel
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
class ClearCompletedViewModelTest {

    private val repository = CompletedRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestClear_conCompletadas_abreConfirmacionConElConteo() = runTest {
        repository.count.value = 3
        val viewModel = viewModel()

        viewModel.requestClear()

        assertEquals(3, viewModel.uiState.value.confirmCount)
    }

    @Test
    fun requestClear_sinCompletadas_noAbre() = runTest {
        val viewModel = viewModel()

        viewModel.requestClear()

        assertNull(viewModel.uiState.value.confirmCount)
    }

    @Test
    fun confirm_borraYCierra() = runTest {
        repository.count.value = 2
        val viewModel = viewModel()
        viewModel.requestClear()

        viewModel.confirm()

        assertEquals(1, repository.deleteCalls)
        assertNull(viewModel.uiState.value.confirmCount)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun confirm_falla_muestraError() = runTest {
        val failure = DomainError.NotFound("TASK_NOT_FOUND", "La tarea no existe")
        repository.failure = failure
        repository.count.value = 2
        val viewModel = viewModel()
        viewModel.requestClear()

        viewModel.confirm()

        assertEquals(failure, viewModel.uiState.value.error)
        assertEquals(2, viewModel.uiState.value.confirmCount)
    }

    @Test
    fun cancel_noBorra() = runTest {
        repository.count.value = 2
        val viewModel = viewModel()
        viewModel.requestClear()

        viewModel.cancel()

        assertEquals(0, repository.deleteCalls)
        assertNull(viewModel.uiState.value.confirmCount)
    }

    @Test
    fun uiState_habilitaLimpiarSegunElConteo() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.clearEnabled)

        repository.count.value = 1

        assertTrue(viewModel.uiState.value.clearEnabled)
    }

    private fun TestScope.viewModel(): ClearCompletedViewModel {
        val viewModel = ClearCompletedViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class CompletedRepository(
        private val delegate: TaskRepository = FakeDestinationTaskRepository(),
    ) : TaskRepository by delegate {
        val count = MutableStateFlow(0)
        var failure: DomainError? = null
        var deleteCalls = 0

        override fun observeCompletedCount(): Flow<Int> = count

        override suspend fun deleteCompleted(): DomainResult<Int> {
            deleteCalls++
            failure?.let { return DomainResult.Failure(it) }
            val deleted = count.value
            count.value = 0
            return DomainResult.Success(deleted)
        }
    }
}
