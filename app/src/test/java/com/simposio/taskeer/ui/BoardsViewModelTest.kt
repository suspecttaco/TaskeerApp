package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.domain.board.newBoard
import com.simposio.taskeer.domain.board.renamed
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.ui.boards.BoardsViewModel
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createBoard_conNombreDeUnCaracter_exponeElErrorEnEspanol() = runTest {
        val viewModel = BoardsViewModel(RuleBackedBoardRepository())
        collect(viewModel)

        viewModel.createBoard("x")

        val error = viewModel.uiState.value.error
        assertEquals("BOARD_NAME_TOO_SHORT", error?.code)
        assertEquals("El nombre del tablero debe tener al menos 2 caracteres", error?.message)
    }

    @Test
    fun createBoard_valido_limpiaElError() = runTest {
        val viewModel = BoardsViewModel(RuleBackedBoardRepository())
        collect(viewModel)
        viewModel.createBoard("x")

        viewModel.createBoard("Compras")

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("Compras"), viewModel.uiState.value.boards.map { it.name })
    }

    @Test
    fun renameBoard_conNombreDemasiadoLargo_exponeElError() = runTest {
        val viewModel = BoardsViewModel(RuleBackedBoardRepository())
        collect(viewModel)
        viewModel.createBoard("Compras")
        val id = viewModel.uiState.value.boards.single().id

        viewModel.renameBoard(id, "x".repeat(151))

        assertEquals("BOARD_NAME_TOO_LONG", viewModel.uiState.value.error?.code)
        assertTrue(viewModel.uiState.value.error?.message?.contains("150") == true)
    }

    private fun TestScope.collect(viewModel: BoardsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private class RuleBackedBoardRepository : BoardRepository {
        private val boards = MutableStateFlow<List<Board>>(emptyList())

        override fun observeBoards(): Flow<List<Board>> = boards

        override suspend fun createBoard(name: String): DomainResult<Board> {
            val result = newBoard(name, Instant.EPOCH)
            if (result is DomainResult.Success) {
                boards.value = boards.value + result.value
            }
            return result
        }

        override suspend fun renameBoard(id: String, name: String): DomainResult<Board> {
            val current = boards.value.first { it.id == id }
            return current.renamed(name, Instant.EPOCH)
        }

        override suspend fun deleteBoard(id: String): DomainResult<Unit> = DomainResult.Success(Unit)
    }
}
