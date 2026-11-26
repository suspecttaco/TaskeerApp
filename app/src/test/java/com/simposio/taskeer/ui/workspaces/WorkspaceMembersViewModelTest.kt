package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspacesState
import com.simposio.taskeer.testing.FakeWorkspaceMembersRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceMembersViewModelTest {

    private val ws = uuid(100)
    private val members = FakeWorkspaceMembersRepository()
    private val workspaces = FakeWorkspaceRepository()
    private val session = MutableStateFlow<SessionState>(signedIn(1))
    private val events = mutableListOf<MembersEvent>()

    private fun signedIn(n: Int): SessionState = SessionState.SignedIn(AccountSummary("m$n@example.com", "Yo"), offline = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun defaultItems() = listOf(
        member(1, WorkspaceRole.OWNER, name = "Ana"),
        member(2, WorkspaceRole.ADMIN, name = "Beto"),
        member(3, WorkspaceRole.EDITOR, name = "Carla"),
        member(4, WorkspaceRole.VIEWER, name = "Dani"),
    )

    private fun TestScope.viewModel(id: String = ws): WorkspaceMembersViewModel {
        val viewModel = WorkspaceMembersViewModel(
            SavedStateHandle(mapOf(WorkspaceMembersViewModel.WORKSPACE_ID_ARG to id)),
            members,
            workspaces,
            session,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun TestScope.opened(selfN: Int = 1, personal: Boolean = false): WorkspaceMembersViewModel {
        val myRole = defaultItems().first { it.userId == uuid(selfN) }.role
        workspaces.mutableState.value =
            WorkspacesState(listOf(workspace(id = ws, name = "Equipo", role = myRole, isPersonal = personal)), loaded = true)
        members.loadItems = defaultItems()
        session.value = signedIn(selfN)
        return viewModel().also { it.onOpened() }
    }

    private fun failed(error: DomainError) = WorkspaceOutcome.Failed(error)

    private fun api(code: String) = failed(DomainError.fromApi(code, "x"))

    @Test
    fun primerLoad_muestraCargandoYLuegoLista() = runTest {
        workspaces.mutableState.value = WorkspacesState(listOf(workspace(id = ws, name = "Equipo")), loaded = true)
        members.loadItems = defaultItems()
        val gate = CompletableDeferred<Unit>()
        members.loadGate = gate
        val viewModel = viewModel()
        assertEquals(MembersPhase.LOADING, viewModel.uiState.value.phase)

        viewModel.onOpened()
        assertEquals(MembersPhase.LOADING, viewModel.uiState.value.phase)
        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(MembersPhase.LIST, state.phase)
        assertEquals(listOf("Ana", "Beto", "Carla", "Dani"), state.rows.map { it.title })
        assertEquals("Equipo", state.title)
        assertEquals(listOf(ws), members.loads)
    }

    @Test
    fun filaPropia_seMarcaComoYo() = runTest {
        val viewModel = opened(selfN = 3)

        assertEquals(listOf(uuid(3)), viewModel.uiState.value.rows.filter { it.isSelf }.map { it.userId })
    }

    @Test
    fun errorSinLista_muestraErrorConReintento() = runTest {
        workspaces.mutableState.value = WorkspacesState(listOf(workspace(id = ws)), loaded = true)
        members.loadResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(MembersPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(WorkspaceMemberErrorMessage.Offline, viewModel.uiState.value.error)

        members.loadResult = WorkspaceOutcome.Done
        members.loadItems = defaultItems()
        viewModel.refresh()

        assertEquals(MembersPhase.LIST, viewModel.uiState.value.phase)
    }

    @Test
    fun errorConLista_conservaLaListaYAvisa() = runTest {
        val viewModel = opened()
        members.loadResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.refresh()

        assertEquals(MembersPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(4, viewModel.uiState.value.rows.size)
        assertEquals(listOf<MembersEvent>(MembersEvent.ShowError(WorkspaceMemberErrorMessage.ServerDown)), events)
    }

    @Test
    fun workspaceDesaparecido_avisaYSale() = runTest {
        val viewModel = opened()
        members.loadResult = api("WORKSPACE_NOT_FOUND")
        val before = workspaces.refreshCalls

        viewModel.refresh()

        assertEquals(
            listOf(MembersEvent.ShowError(WorkspaceMemberErrorMessage.WorkspaceGone), MembersEvent.LeaveScreen),
            events,
        )
        assertEquals(before + 1, workspaces.refreshCalls)
    }

    @Test
    fun workspaceAusenteAlAbrir_refrescaUnaVezYSale() = runTest {
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(1, workspaces.refreshCalls)
        assertTrue(members.loads.isEmpty())
        assertEquals(listOf(MembersEvent.ShowError(WorkspaceMemberErrorMessage.WorkspaceGone), MembersEvent.LeaveScreen), events.takeLast(2))
    }

    @Test
    fun workspaceAusenteAlAbrir_queAparecePorRefresco_cargaMiembros() = runTest {
        workspaces.refreshItems = listOf(workspace(id = ws, name = "Equipo"))
        members.loadItems = defaultItems()
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(listOf(ws), members.loads)
        assertEquals(MembersPhase.LIST, viewModel.uiState.value.phase)
    }

    @Test
    fun idDeRutaNoUuid_avisaYSale() = runTest {
        val viewModel = viewModel(id = "../x")

        viewModel.onOpened()

        assertTrue(members.loads.isEmpty())
        assertEquals(listOf(MembersEvent.ShowError(WorkspaceMemberErrorMessage.WorkspaceGone), MembersEvent.LeaveScreen), events)
    }

    @Test
    fun cambiarRol_conExito_cierraElDialogo() = runTest {
        val viewModel = opened(selfN = 2)
        viewModel.openChangeRole(uuid(4))
        assertTrue(viewModel.uiState.value.dialog is MemberDialog.ChangeRole)

        viewModel.changeRole(WorkspaceRole.EDITOR)

        assertEquals(listOf(uuid(4) to WorkspaceRole.EDITOR), members.roleChanges)
        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(!viewModel.uiState.value.submitting)
    }

    @Test
    fun cambiarRol_sinPermiso_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 3)

        viewModel.openChangeRole(uuid(4))
        viewModel.changeRole(WorkspaceRole.ADMIN)

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.roleChanges.isEmpty())
    }

    @Test
    fun cambiarRol_sobreOwner_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)

        viewModel.openChangeRole(uuid(1))

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.roleChanges.isEmpty())
    }

    @Test
    fun cambiarRol_mismoRol_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)
        viewModel.openChangeRole(uuid(4))

        viewModel.changeRole(WorkspaceRole.VIEWER)

        assertTrue(members.roleChanges.isEmpty())
    }

    @Test
    fun cambiarRol_aOwner_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)
        viewModel.openChangeRole(uuid(4))

        viewModel.changeRole(WorkspaceRole.OWNER)

        assertTrue(members.roleChanges.isEmpty())
    }

    @Test
    fun cambiarRol_403_avisaYRecarga() = runTest {
        val viewModel = opened(selfN = 2)
        members.mutationResult = api("FORBIDDEN")
        val loadsBefore = members.loads.size
        val refreshesBefore = workspaces.refreshCalls
        viewModel.openChangeRole(uuid(4))

        viewModel.changeRole(WorkspaceRole.EDITOR)

        assertEquals(listOf<MembersEvent>(MembersEvent.ShowError(WorkspaceMemberErrorMessage.ForbiddenAction)), events)
        assertEquals(loadsBefore + 1, members.loads.size)
        assertEquals(refreshesBefore + 1, workspaces.refreshCalls)
        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun cambiarRol_dobleToque_hacePeticionUnica() = runTest {
        val viewModel = opened(selfN = 2)
        members.mutationGate = CompletableDeferred()
        viewModel.openChangeRole(uuid(4))

        viewModel.changeRole(WorkspaceRole.EDITOR)
        viewModel.changeRole(WorkspaceRole.EDITOR)

        assertEquals(1, members.roleChanges.size)
        assertTrue(viewModel.uiState.value.submitting)
    }

    @Test
    fun expulsar_exigeConfirmacion() = runTest {
        val viewModel = opened(selfN = 2)

        viewModel.requestRemove(uuid(4))

        assertEquals(MemberDialog.ConfirmRemove(uuid(4), "Dani"), viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())

        viewModel.confirm()

        assertEquals(listOf(uuid(4)), members.removals)
        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun cancelarConfirmacion_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)
        viewModel.requestRemove(uuid(4))

        viewModel.dismissDialog()
        viewModel.confirm()

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())
    }

    @Test
    fun expulsar_sinPermiso_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 4)

        viewModel.requestRemove(uuid(3))
        viewModel.confirm()

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())
    }

    @Test
    fun expulsar_aOwner_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)

        viewModel.requestRemove(uuid(1))

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun salir_exigeConfirmacion() = runTest {
        val viewModel = opened(selfN = 3)

        viewModel.requestLeave()

        assertEquals(MemberDialog.ConfirmLeave, viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())
    }

    @Test
    fun salir_conExito_refrescaWorkspacesYSale() = runTest {
        val viewModel = opened(selfN = 3)
        val refreshesBefore = workspaces.refreshCalls
        viewModel.requestLeave()

        viewModel.confirm()

        assertEquals(listOf(uuid(3)), members.removals)
        assertEquals(refreshesBefore + 1, workspaces.refreshCalls)
        assertEquals(listOf(MembersEvent.ShowMessage(MembersNotice.Left("Equipo")), MembersEvent.LeaveScreen), events)
    }

    @Test
    fun salir_owner_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 1)

        viewModel.requestLeave()
        viewModel.confirm()

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())
        assertTrue(viewModel.uiState.value.ownerHint)
    }

    @Test
    fun salir_sinIdentificarme_noHacePeticion() = runTest {
        workspaces.mutableState.value = WorkspacesState(listOf(workspace(id = ws, role = WorkspaceRole.EDITOR)), loaded = true)
        members.loadItems = defaultItems()
        session.value = SessionState.SignedIn(AccountSummary("otra@example.com", "Otra"), offline = false)
        val viewModel = viewModel().also { it.onOpened() }

        viewModel.requestLeave()
        viewModel.confirm()

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.removals.isEmpty())
        assertTrue(viewModel.uiState.value.rows.none { it.isSelf })
    }

    @Test
    fun salir_workspaceDesaparecido_equivaleASalir() = runTest {
        val viewModel = opened(selfN = 3)
        members.mutationResult = api("WORKSPACE_NOT_FOUND")
        viewModel.requestLeave()

        viewModel.confirm()

        assertEquals(listOf(MembersEvent.ShowMessage(MembersNotice.Left("Equipo")), MembersEvent.LeaveScreen), events)
    }

    @Test
    fun salir_ownerObsoleto_avisaYRecarga() = runTest {
        val viewModel = opened(selfN = 3)
        members.mutationResult = api("OWNER_MUST_TRANSFER_OWNERSHIP")
        val loadsBefore = members.loads.size
        viewModel.requestLeave()

        viewModel.confirm()

        assertEquals(listOf<MembersEvent>(MembersEvent.ShowError(WorkspaceMemberErrorMessage.OwnerMustTransfer)), events)
        assertEquals(loadsBefore + 1, members.loads.size)
    }

    @Test
    fun transferir_exigeConfirmacion() = runTest {
        val viewModel = opened(selfN = 1)

        viewModel.requestTransfer(uuid(2))

        assertEquals(MemberDialog.ConfirmTransfer(uuid(2), "Beto"), viewModel.uiState.value.dialog)
        assertTrue(members.transfers.isEmpty())
    }

    @Test
    fun transferir_sinSerOwner_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 2)

        viewModel.requestTransfer(uuid(3))
        viewModel.confirm()

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.transfers.isEmpty())
    }

    @Test
    fun transferir_aUnoMismo_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 1)

        viewModel.requestTransfer(uuid(1))

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.transfers.isEmpty())
    }

    @Test
    fun transferir_personal_noHacePeticion() = runTest {
        val viewModel = opened(selfN = 1, personal = true)

        viewModel.requestTransfer(uuid(2))

        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
        assertTrue(members.transfers.isEmpty())
    }

    @Test
    fun transferir_conExito_recargaMiembrosYWorkspaces() = runTest {
        val viewModel = opened(selfN = 1)
        val loadsBefore = members.loads.size
        val refreshesBefore = workspaces.refreshCalls
        viewModel.requestTransfer(uuid(2))

        viewModel.confirm()

        assertEquals(listOf(uuid(2)), members.transfers)
        assertEquals(loadsBefore + 1, members.loads.size)
        assertEquals(refreshesBefore + 1, workspaces.refreshCalls)
        assertEquals(listOf<MembersEvent>(MembersEvent.ShowMessage(MembersNotice.OwnershipTransferred("Beto"))), events)
        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun transferir_conRolReleido_quitaLasAccionesDeOwner() = runTest {
        val viewModel = opened(selfN = 1)
        viewModel.requestTransfer(uuid(2))
        members.loadItems = listOf(
            member(1, WorkspaceRole.ADMIN, name = "Ana"),
            member(2, WorkspaceRole.OWNER, name = "Beto"),
            member(3, WorkspaceRole.EDITOR, name = "Carla"),
        )

        viewModel.confirm()

        val own = viewModel.uiState.value.rows.first { it.isSelf }
        assertTrue(MemberAction.TRANSFER !in viewModel.uiState.value.rows.flatMap { it.actions })
        assertEquals(RoleLabel.ADMIN, own.role)
        assertTrue(MemberAction.LEAVE in own.actions)
    }

    @Test
    fun falloDeAccion_emiteShowError() = runTest {
        val viewModel = opened(selfN = 2)
        members.mutationResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        viewModel.requestRemove(uuid(4))

        viewModel.confirm()

        assertEquals(listOf<MembersEvent>(MembersEvent.ShowError(WorkspaceMemberErrorMessage.Offline)), events)
        assertEquals(MemberDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        opened()

        session.value = SessionState.SignedOut(expired = true)

        assertEquals(listOf<MembersEvent>(MembersEvent.LeaveScreen), events)
    }

    @Test
    fun miembrosDeOtroWorkspace_noSeMuestran() = runTest {
        members.mutableState.value = MembersState(uuid(200), defaultItems(), loaded = true)
        workspaces.mutableState.value = WorkspacesState(listOf(workspace(id = ws)), loaded = true)
        members.loadGate = CompletableDeferred()
        val viewModel = viewModel().also { it.onOpened() }

        assertEquals(emptyList<MemberRowUi>(), viewModel.uiState.value.rows)
        assertEquals(MembersPhase.LOADING, viewModel.uiState.value.phase)
    }

    @Test
    fun filaTableros_emiteAbrirTableros() = runTest {
        val viewModel = opened()

        viewModel.openBoards()

        assertEquals(listOf<MembersEvent>(MembersEvent.OpenBoards(ws)), events)
    }

    @Test
    fun filaTableros_estaDisponibleParaCualquierRol() = runTest {
        val viewModel = opened(selfN = 4)

        viewModel.openBoards()

        assertEquals(listOf<MembersEvent>(MembersEvent.OpenBoards(ws)), events)
    }
}
