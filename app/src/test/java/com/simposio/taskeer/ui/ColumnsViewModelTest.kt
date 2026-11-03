package com.simposio.taskeer.ui

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.data.repository.ColumnRepository
import com.simposio.taskeer.domain.model.Column
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.ui.columns.ColumnsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ColumnsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setWipLimit_conValor_delegaEnElRepositorio() = runTest {
        val repository = FakeColumnRepository()

        ColumnsViewModel(repository, handle()).setWipLimit("c1", 3)

        assertEquals(listOf("c1" to 3), repository.calls)
    }

    @Test
    fun setWipLimit_conNulo_quitaElLimite() = runTest {
        val repository = FakeColumnRepository()

        ColumnsViewModel(repository, handle()).setWipLimit("c1", null)

        assertEquals(listOf<Pair<String, Int?>>("c1" to null), repository.calls)
    }

    @Test
    fun setWipLimit_conCero_exponeElErrorDelRepositorio() = runTest {
        val repository = FakeColumnRepository().apply {
            failure = DomainError.Validation("COLUMN_WIP_LIMIT_INVALID", "El valor mínimo del límite WIP es 1")
        }
        val viewModel = ColumnsViewModel(repository, handle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.setWipLimit("c1", 0)

        assertEquals("COLUMN_WIP_LIMIT_INVALID", viewModel.uiState.value.error?.code)
    }

    private fun handle() = SavedStateHandle(mapOf(ColumnsViewModel.BOARD_ID_ARG to "tablero-1"))

    private class FakeColumnRepository : ColumnRepository {
        val calls: MutableList<Pair<String, Int?>> = mutableListOf()
        var failure: DomainError? = null

        override fun observeColumns(boardId: String): Flow<List<Column>> = flowOf(emptyList())

        override suspend fun setWipLimit(columnId: String, limit: Int?): DomainResult<Column> {
            calls += columnId to limit
            return failure?.let { DomainResult.Failure(it) } ?: DomainResult.Failure(DomainError.Unknown("UNKNOWN", "stub"))
        }

        override suspend fun createColumn(boardId: String, name: String): DomainResult<Column> = error("no se usa")

        override suspend fun renameColumn(id: String, name: String): DomainResult<Column> = error("no se usa")

        override suspend fun moveColumn(id: String, toIndex: Int): DomainResult<Column> = error("no se usa")

        override suspend fun deleteColumn(id: String): DomainResult<Unit> = error("no se usa")
    }
}
