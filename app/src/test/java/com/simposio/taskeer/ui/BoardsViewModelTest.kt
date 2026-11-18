package com.simposio.taskeer.ui

import com.simposio.taskeer.data.repository.BoardRepository
import com.simposio.taskeer.domain.board.newBoard
import com.simposio.taskeer.domain.board.renamed
import com.simposio.taskeer.domain.model.Board
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.ui.boards.BoardDialog
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
    fun openCreate_abreElDialogo() = runTest {
        val viewModel = viewModel()

        viewModel.openCreate()

        assertEquals(BoardDialog.Create, viewModel.uiState.value.dialog)
    }

    @Test
    fun submitName_crea_cierraYMuestraElTablero() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.submitName("Compras")

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("Compras"), viewModel.uiState.value.boards.map { it.name })
    }

    @Test
    fun submitName_invalido_muestraErrorYNoCierra() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.submitName("x")

        assertEquals(BoardDialog.Create, viewModel.uiState.value.dialog)
        val error = viewModel.uiState.value.error
        assertEquals("BOARD_NAME_TOO_SHORT", error?.code)
        assertEquals("El nombre del tablero debe tener al menos 2 caracteres", error?.message)
    }

    @Test
    fun dismiss_cierraYLimpiaElError() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()
        viewModel.submitName("x")

        viewModel.dismiss()

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun uiState_exponeTableros() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()
        viewModel.submitName("Compras")
        viewModel.openCreate()
        viewModel.submitName("Viajes")

        assertEquals(listOf("Compras", "Viajes"), viewModel.uiState.value.boards.map { it.name })
    }

    @Test
    fun uiState_sinTableros_listaVacia() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.boards.isEmpty())
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun openRename_conNombreActual() = runTest {
        val viewModel = viewModel()
        val id = createBoard(viewModel, "Compras")

        viewModel.openRename(id)

        assertEquals(BoardDialog.Rename(id, "Compras"), viewModel.uiState.value.dialog)
    }

    @Test
    fun submitName_renombra() = runTest {
        val viewModel = viewModel()
        val id = createBoard(viewModel, "Compras")
        viewModel.openRename(id)

        viewModel.submitName("Super")

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(listOf("Super"), viewModel.uiState.value.boards.map { it.name })
    }

    @Test
    fun submitName_nombreDuplicadoOInvalido_muestraError() = runTest {
        val viewModel = viewModel()
        val id = createBoard(viewModel, "Compras")
        viewModel.openRename(id)

        viewModel.submitName("x".repeat(151))

        assertTrue(viewModel.uiState.value.dialog is BoardDialog.Rename)
        assertEquals("BOARD_NAME_TOO_LONG", viewModel.uiState.value.error?.code)
        assertTrue(viewModel.uiState.value.error?.message?.contains("150") == true)
    }

    @Test
    fun openDelete_pideConfirmacion() = runTest {
        val repository = RuleBackedBoardRepository()
        val viewModel = viewModel(repository)
        val id = createBoard(viewModel, "Compras")

        viewModel.openDelete(id)

        assertEquals(BoardDialog.ConfirmDelete(id, "Compras"), viewModel.uiState.value.dialog)
        assertTrue("sin confirmar no borra", repository.deleted.isEmpty())
    }

    @Test
    fun confirmDelete_borraYLimpiaElDialogo() = runTest {
        val repository = RuleBackedBoardRepository()
        val viewModel = viewModel(repository)
        val id = createBoard(viewModel, "Compras")
        viewModel.openDelete(id)

        viewModel.confirmDelete()

        assertEquals(listOf(id), repository.deleted)
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(id, viewModel.uiState.value.deletedBoardId)
    }

    private fun TestScope.createBoard(viewModel: BoardsViewModel, name: String): String {
        viewModel.openCreate()
        viewModel.submitName(name)
        return viewModel.uiState.value.boards.single { it.name == name }.id
    }

    private fun TestScope.viewModel(repository: BoardRepository = RuleBackedBoardRepository()): BoardsViewModel =
        BoardsViewModel(repository).also { collect(it) }

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
            val result = current.renamed(name, Instant.EPOCH)
            if (result is DomainResult.Success) {
                boards.value = boards.value.map { if (it.id == id) result.value else it }
            }
            return result
        }

        val deleted = mutableListOf<String>()

        override suspend fun deleteBoard(id: String): DomainResult<Unit> {
            deleted += id
            return DomainResult.Success(Unit)
        }
    }
}
