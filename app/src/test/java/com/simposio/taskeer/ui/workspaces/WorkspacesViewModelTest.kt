package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspacesState
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspacesViewModelTest {

    private val repo = FakeWorkspaceRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(AccountSummary("a@x.com", "A"), offline = false))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): WorkspacesViewModel {
        val viewModel = WorkspacesViewModel(repo, session)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun loaded(vararg items: com.simposio.taskeer.domain.workspace.Workspace) {
        repo.mutableState.value = WorkspacesState(items.toList(), loaded = true)
    }

    private fun failed(error: DomainError, retryAfter: Long? = null) = WorkspaceOutcome.Failed(error, retryAfter)

    private suspend fun WorkspacesViewModel.firstEvent(): WorkspacesEvent? = withTimeoutOrNull(50) { events.first() }

    @Test
    fun primerRefresco_muestraCargandoYLuegoLista() = runTest {
        val viewModel = viewModel()
        repo.refreshItems = listOf(workspace(id = uuid(1), name = "Uno"), workspace(id = uuid(2), name = "Dos"))
        val gate = CompletableDeferred<Unit>()
        repo.refreshGate = gate
        assertEquals(WorkspacesPhase.LOADING, viewModel.uiState.value.phase)

        viewModel.onOpened()
        assertEquals(WorkspacesPhase.LOADING, viewModel.uiState.value.phase)
        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(WorkspacesPhase.LIST, state.phase)
        assertEquals(listOf("Dos", "Uno"), state.rows.map { it.name })
    }

    @Test
    fun onOpened_soloRefrescaLaPrimeraVez() = runTest {
        val viewModel = viewModel()

        viewModel.onOpened()
        viewModel.onOpened()

        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun listaVacia_muestraVacio() = runTest {
        val viewModel = viewModel()
        repo.refreshItems = emptyList()

        viewModel.onOpened()

        assertEquals(WorkspacesPhase.EMPTY, viewModel.uiState.value.phase)
    }

    @Test
    fun errorSinLista_muestraErrorConReintento() = runTest {
        val viewModel = viewModel()
        repo.refreshResult = failed(DomainError.Network("NETWORK_ERROR", "x"))

        viewModel.onOpened()

        assertEquals(WorkspacesPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(WorkspaceErrorMessage.Offline, viewModel.uiState.value.error)
        repo.refreshResult = WorkspaceOutcome.Done
        repo.refreshItems = listOf(workspace())
        viewModel.refresh()
        assertEquals(WorkspacesPhase.LIST, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun errorConLista_conservaLaListaYAvisa() = runTest {
        val viewModel = viewModel()
        loaded(workspace(name = "Uno"))
        repo.refreshResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.refresh()

        assertEquals(WorkspacesPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(listOf("Uno"), viewModel.uiState.value.rows.map { it.name })
        assertEquals(WorkspacesEvent.ShowError(WorkspaceErrorMessage.ServerDown), viewModel.firstEvent())
    }

    @Test
    fun crear_conExito_cierraElDialogo() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.confirmName("  Nuevo ")

        assertEquals(listOf("Nuevo"), repo.created)
        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
        assertEquals(false, viewModel.uiState.value.submitting)
    }

    @Test
    fun crear_muestraProgresoMientrasEspera() = runTest {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        repo.mutationGate = gate
        viewModel.openCreate()

        viewModel.confirmName("Nuevo")
        viewModel.confirmName("Nuevo")

        assertTrue(viewModel.uiState.value.submitting)
        assertEquals(1, repo.created.size)
        gate.complete(Unit)
        assertEquals(false, viewModel.uiState.value.submitting)
    }

    @Test
    fun crear_limite_dejaElDialogoConElMensaje() = runTest {
        val viewModel = viewModel()
        repo.mutationResult = failed(DomainError.fromApi("WORKSPACE_LIMIT_REACHED", "x"))
        viewModel.openCreate()

        viewModel.confirmName("Nuevo")

        assertEquals(WorkspaceDialog.Create, viewModel.uiState.value.dialog)
        assertEquals(WorkspaceErrorMessage.PlanLimit, viewModel.uiState.value.dialogError)
    }

    @Test
    fun crear_nombreInvalido_muestraErrorEnElCampo() = runTest {
        val viewModel = viewModel()
        viewModel.openCreate()

        viewModel.confirmName("a")

        assertEquals(WorkspaceErrorMessage.InvalidName, viewModel.uiState.value.dialogError)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun renombrar_conExito_cierraElDialogo() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1), name = "Viejo"))

        viewModel.openRename(uuid(1))
        assertEquals(WorkspaceDialog.Rename(uuid(1), "Viejo"), viewModel.uiState.value.dialog)
        viewModel.confirmName("Nuevo")

        assertEquals(listOf(uuid(1) to "Nuevo"), repo.renamed)
        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun renombrar_sinRol_noAbreElDialogo() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1), role = WorkspaceRole.EDITOR))

        viewModel.openRename(uuid(1))

        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun eliminar_exigeConfirmacion() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1), name = "Equipo"))

        viewModel.requestDelete(uuid(1))

        assertEquals(WorkspaceDialog.ConfirmDelete(uuid(1), "Equipo"), viewModel.uiState.value.dialog)
        assertTrue(repo.deleted.isEmpty())
        viewModel.confirmDelete()
        assertEquals(listOf(uuid(1)), repo.deleted)
        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun eliminar_conAdmin_noPideConfirmacion() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1), role = WorkspaceRole.ADMIN))

        viewModel.requestDelete(uuid(1))

        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun cancelarConfirmacion_noHacePeticion() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1)))
        viewModel.requestDelete(uuid(1))

        viewModel.dismissDialog()

        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun eliminar_403_avisaYRefrescaLaLista() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1)))
        repo.mutationResult = failed(DomainError.fromApi("FORBIDDEN", "x"))
        viewModel.requestDelete(uuid(1))

        viewModel.confirmDelete()

        assertEquals(WorkspacesEvent.ShowError(WorkspaceErrorMessage.OnlyOwnerCanDelete), viewModel.firstEvent())
        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun falloDeAccion_emiteShowError() = runTest {
        val viewModel = viewModel()
        loaded(workspace(id = uuid(1)))
        repo.mutationResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        viewModel.requestDelete(uuid(1))

        viewModel.confirmDelete()

        assertEquals(WorkspacesEvent.ShowError(WorkspaceErrorMessage.Offline), viewModel.firstEvent())
        assertEquals(WorkspaceDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun falloAlCrear_conservaElDialogoYAvisa() = runTest {
        val viewModel = viewModel()
        repo.mutationResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))
        viewModel.openCreate()

        viewModel.confirmName("Nuevo")

        assertEquals(WorkspacesEvent.ShowError(WorkspaceErrorMessage.ServerDown), viewModel.firstEvent())
        assertEquals(WorkspaceDialog.Create, viewModel.uiState.value.dialog)
    }

    @Test
    fun tocarFila_emiteAbrirMiembros() = runTest {
        loaded(workspace(id = uuid(1), name = "Uno", role = WorkspaceRole.VIEWER))
        val viewModel = viewModel()

        viewModel.openMembers(uuid(1))

        assertEquals(WorkspacesEvent.OpenMembers(uuid(1)), viewModel.firstEvent())
    }

    @Test
    fun tocarFila_deWorkspaceDesconocido_noEmiteNada() = runTest {
        loaded(workspace(id = uuid(1)))
        val viewModel = viewModel()

        viewModel.openMembers(uuid(9))

        assertNull(viewModel.firstEvent())
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        val viewModel = viewModel()

        session.value = SessionState.SignedOut(expired = true)

        assertEquals(WorkspacesEvent.LeaveScreen, viewModel.firstEvent())
    }
}
