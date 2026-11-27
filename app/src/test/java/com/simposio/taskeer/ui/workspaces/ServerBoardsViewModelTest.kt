package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.testing.FakeServerBoardsRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.serverBoard
import com.simposio.taskeer.testing.uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerBoardsViewModelTest {

    private val ws = uuid(100)
    private val boards = FakeServerBoardsRepository()
    private val workspaces = FakeWorkspaceRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(AccountSummary("yo@example.com", "Yo"), false))
    private val events = mutableListOf<ServerBoardsEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(id: String = ws): ServerBoardsViewModel {
        val viewModel = ServerBoardsViewModel(
            SavedStateHandle(mapOf(ServerBoardsViewModel.WORKSPACE_ID_ARG to id)),
            boards,
            workspaces,
            session,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun failed(error: DomainError) = WorkspaceOutcome.Failed(error)

    @Test
    fun primerLoad_muestraCargandoYLuegoLista() = runTest {
        boards.loadItems = listOf(serverBoard(2, "Beta"), serverBoard(1, "Alfa"))
        val gate = CompletableDeferred<Unit>()
        boards.loadGate = gate
        val viewModel = viewModel()

        viewModel.onOpened()
        assertEquals(ServerBoardsPhase.LOADING, viewModel.uiState.value.phase)
        gate.complete(Unit)

        assertEquals(ServerBoardsPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(listOf("Alfa", "Beta"), viewModel.uiState.value.rows.map { it.name })
        assertEquals(listOf(ws), boards.loads)
    }

    @Test
    fun errorSinLista_muestraErrorConReintento() = runTest {
        boards.loadResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(ServerBoardsPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(BoardRoleErrorMessage.Offline, viewModel.uiState.value.error)

        boards.loadResult = WorkspaceOutcome.Done
        boards.loadItems = listOf(serverBoard(1))
        viewModel.refresh()

        assertEquals(ServerBoardsPhase.LIST, viewModel.uiState.value.phase)
    }

    @Test
    fun errorConLista_conservaLaListaYAvisa() = runTest {
        boards.loadItems = listOf(serverBoard(1))
        val viewModel = viewModel().also { it.onOpened() }
        boards.loadResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.refresh()

        assertEquals(ServerBoardsPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals(listOf<ServerBoardsEvent>(ServerBoardsEvent.ShowError(BoardRoleErrorMessage.ServerDown)), events)
    }

    @Test
    fun vacio_muestraEstadoVacio() = runTest {
        boards.loadItems = emptyList()
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(ServerBoardsPhase.EMPTY, viewModel.uiState.value.phase)
    }

    @Test
    fun tocarTablero_emiteAbrirAcceso() = runTest {
        boards.loadItems = listOf(serverBoard(1))
        val viewModel = viewModel().also { it.onOpened() }

        viewModel.openBoard(uuid(1001))

        assertEquals(listOf<ServerBoardsEvent>(ServerBoardsEvent.OpenAccess(uuid(1001))), events)
    }

    @Test
    fun tocarTableroDesconocido_noEmiteNada() = runTest {
        boards.loadItems = listOf(serverBoard(1))
        val viewModel = viewModel().also { it.onOpened() }

        viewModel.openBoard(uuid(9999))

        assertEquals(emptyList<ServerBoardsEvent>(), events)
    }

    @Test
    fun idDeRutaNoUuid_avisaYSale() = runTest {
        val viewModel = viewModel(id = "../x")

        viewModel.onOpened()

        assertEquals(
            listOf(ServerBoardsEvent.ShowError(BoardRoleErrorMessage.BoardGone), ServerBoardsEvent.LeaveScreen),
            events,
        )
        assertEquals(emptyList<String>(), boards.loads)
    }

    @Test
    fun workspaceDesaparecido_avisaYSale() = runTest {
        boards.loadResult = failed(DomainError.NotFound("WORKSPACE_NOT_FOUND", "x"))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(
            listOf(ServerBoardsEvent.ShowError(BoardRoleErrorMessage.BoardGone), ServerBoardsEvent.LeaveScreen),
            events,
        )
        assertEquals(1, workspaces.refreshCalls)
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        viewModel().also { it.onOpened() }

        session.value = SessionState.SignedOut(expired = true)

        assertEquals(listOf<ServerBoardsEvent>(ServerBoardsEvent.LeaveScreen), events)
    }

    @Test
    fun toStringDeLaFila_noIncluyeElNombre() {
        assertEquals("BoardRowUi(redactada)", BoardRowUi(uuid(1001), "Secreto").toString())
    }
}
