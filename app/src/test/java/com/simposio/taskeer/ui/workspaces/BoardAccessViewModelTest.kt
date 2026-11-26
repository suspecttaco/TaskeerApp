package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.BoardCapability
import com.simposio.taskeer.domain.workspace.BoardMemberOverride
import com.simposio.taskeer.domain.workspace.BoardRoleRules
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceMember
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspacesState
import com.simposio.taskeer.testing.FakeBoardAccessRepository
import com.simposio.taskeer.testing.FakeRoleChangeSignals
import com.simposio.taskeer.testing.FakeWorkspaceMembersRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.boardOverride
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardAccessViewModelTest {

    private val ws = uuid(100)
    private val board = uuid(1001)
    private val access = FakeBoardAccessRepository()
    private val members = FakeWorkspaceMembersRepository()
    private val workspaces = FakeWorkspaceRepository()
    private val signals = FakeRoleChangeSignals()
    private val session = MutableStateFlow<SessionState>(signedIn(2))
    private val events = mutableListOf<BoardAccessEvent>()

    private fun signedIn(n: Int): SessionState = SessionState.SignedIn(AccountSummary("m$n@example.com", "Yo"), offline = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun defaultItems(selfN: Int = 2, selfRole: WorkspaceRole = WorkspaceRole.ADMIN): List<WorkspaceMember> = listOf(
        member(1, WorkspaceRole.OWNER, name = "Ana"),
        member(2, WorkspaceRole.ADMIN, name = "Beto"),
        member(3, WorkspaceRole.EDITOR, name = "Carla"),
        member(4, WorkspaceRole.VIEWER, name = "Dani"),
    ).map { if (it.userId == uuid(selfN)) it.copy(role = selfRole) else it }

    private fun TestScope.viewModel(workspaceId: String = ws, boardId: String = board): BoardAccessViewModel {
        val viewModel = BoardAccessViewModel(
            SavedStateHandle(
                mapOf(
                    BoardAccessViewModel.WORKSPACE_ID_ARG to workspaceId,
                    BoardAccessViewModel.BOARD_ID_ARG to boardId,
                ),
            ),
            access,
            members,
            workspaces,
            signals,
            session,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun TestScope.opened(
        workspaceRole: WorkspaceRole = WorkspaceRole.ADMIN,
        selfN: Int = 2,
        overrides: List<BoardMemberOverride> = emptyList(),
        effective: WorkspaceRole = WorkspaceRole.EDITOR,
        items: List<WorkspaceMember> = defaultItems(selfN, workspaceRole),
    ): BoardAccessViewModel {
        workspaces.mutableState.value =
            WorkspacesState(listOf(workspace(id = ws, name = "Equipo", role = workspaceRole)), loaded = true)
        members.loadItems = items
        access.effectiveRoleOnRead = effective
        access.overridesOnLoad = overrides
        session.value = signedIn(selfN)
        return viewModel().also { it.onOpened() }
    }

    private fun failed(error: DomainError, retryAfter: Long? = null) = WorkspaceOutcome.Failed(error, retryAfter)

    private fun api(code: String) = failed(DomainError.fromApi(code, "x"))

    private fun TestScope.demoteSelfToViewer() {
        workspaces.refreshItems = listOf(workspace(id = ws, name = "Equipo", role = WorkspaceRole.VIEWER))
        members.loadItems = defaultItems(2, WorkspaceRole.VIEWER)
    }

    @Test
    fun noGestor_noPideOverrides() = runTest {
        val viewModel = opened(workspaceRole = WorkspaceRole.EDITOR, selfN = 3)

        assertEquals(emptyList<String>(), members.loads)
        assertEquals(0, access.overrideLoads)
        assertFalse(viewModel.uiState.value.showPanel)
        assertEquals(RoleLabel.EDITOR, viewModel.uiState.value.roleLabel)
    }

    @Test
    fun gestor_cargaMiembrosYOverrides() = runTest {
        val viewModel = opened(overrides = listOf(boardOverride(3, WorkspaceRole.VIEWER)))

        val state = viewModel.uiState.value
        assertEquals(listOf(ws), members.loads)
        assertEquals(1, access.overrideLoads)
        assertTrue(state.showPanel)
        assertEquals(listOf("Carla", "Dani"), state.rows.map { it.title })
        assertEquals(BoardRoleDisplay.VIEWER, state.rows.first().boardRole)
        assertEquals(BoardRoleDisplay.SAME_AS_WORKSPACE, state.rows.last().boardRole)
    }

    @Test
    fun errorDeOverrides_noTumbaElRolEfectivo() = runTest {
        access.overridesResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        val viewModel = opened()

        val state = viewModel.uiState.value
        assertEquals(BoardAccessPhase.READY, state.phase)
        assertEquals(RoleLabel.EDITOR, state.roleLabel)
        assertTrue(state.panelError)
        assertEquals(emptyList<OverrideRowUi>(), state.rows)
    }

    @Test
    fun workspaceDeUnSoloMiembro_noHayOtrosMiembros() = runTest {
        val viewModel = opened(items = listOf(member(2, WorkspaceRole.ADMIN)))

        assertTrue(viewModel.uiState.value.noOtherMembers)
    }

    @Test
    fun fijar_sinPermiso_noHacePeticion() = runTest {
        val viewModel = opened(workspaceRole = WorkspaceRole.EDITOR, selfN = 3)
        members.mutableState.value = MembersState(ws, defaultItems(3, WorkspaceRole.EDITOR), loaded = true)

        viewModel.openSetRole(uuid(4))
        viewModel.setRole(WorkspaceRole.EDITOR)

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(emptyList<Pair<String, WorkspaceRole>>(), access.sets)
    }

    @Test
    fun fijar_aOwnerOYo_noHacePeticion() = runTest {
        val viewModel = opened()

        viewModel.openSetRole(uuid(1))
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        viewModel.openSetRole(uuid(2))
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)

        assertEquals(emptyList<Pair<String, WorkspaceRole>>(), access.sets)
    }

    @Test
    fun fijar_conExito_cierraElDialogo() = runTest {
        val viewModel = opened()

        viewModel.openSetRole(uuid(3))
        val dialog = viewModel.uiState.value.dialog as BoardDialog.SetRole
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.VIEWER), dialog.options)
        viewModel.setRole(WorkspaceRole.VIEWER)

        assertEquals(listOf(uuid(3) to WorkspaceRole.VIEWER), access.sets)
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(BoardRoleDisplay.VIEWER, viewModel.uiState.value.rows.first { it.userId == uuid(3) }.boardRole)
    }

    @Test
    fun fijar_rolActualDelTablero_noSeOfrece() = runTest {
        val viewModel = opened(overrides = listOf(boardOverride(3, WorkspaceRole.VIEWER)))

        viewModel.openSetRole(uuid(3))

        val dialog = viewModel.uiState.value.dialog as BoardDialog.SetRole
        assertEquals(listOf(WorkspaceRole.ADMIN, WorkspaceRole.EDITOR), dialog.options)
    }

    @Test
    fun fijar_403_avisaYRecarga() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        access.mutationResult = api("FORBIDDEN")

        viewModel.setRole(WorkspaceRole.VIEWER)

        assertTrue(BoardAccessEvent.ShowError(BoardRoleErrorMessage.ForbiddenAction) in events)
        assertEquals(2, members.loads.size)
        assertEquals(1, workspaces.refreshCalls)
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun fijar_422SinMiembro_avisaYRecargaMiembros() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        access.mutationResult = api("USER_NOT_WORKSPACE_MEMBER")

        viewModel.setRole(WorkspaceRole.VIEWER)

        assertTrue(BoardAccessEvent.ShowError(BoardRoleErrorMessage.NotAMember) in events)
        assertEquals(2, members.loads.size)
    }

    @Test
    fun fijar_tableroDesaparecido_avisaYSale() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        access.mutationResult = api("BOARD_NOT_FOUND")

        viewModel.setRole(WorkspaceRole.VIEWER)

        assertEquals(
            listOf(BoardAccessEvent.ShowError(BoardRoleErrorMessage.BoardGone), BoardAccessEvent.LeaveScreen),
            events,
        )
    }

    @Test
    fun fijar_admin_exigeConfirmacion() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))

        viewModel.setRole(WorkspaceRole.ADMIN)

        assertTrue(viewModel.uiState.value.dialog is BoardDialog.ConfirmAdmin)
        assertEquals(emptyList<Pair<String, WorkspaceRole>>(), access.sets)
        viewModel.confirm()
        assertEquals(listOf(uuid(3) to WorkspaceRole.ADMIN), access.sets)
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun cancelarConfirmacion_noHacePeticion() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        viewModel.setRole(WorkspaceRole.ADMIN)

        viewModel.dismissDialog()

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(emptyList<Pair<String, WorkspaceRole>>(), access.sets)
    }

    @Test
    fun quitar_exigeConfirmacion() = runTest {
        val viewModel = opened(overrides = listOf(boardOverride(3)))

        viewModel.requestRemove(uuid(3))

        val dialog = viewModel.uiState.value.dialog as BoardDialog.ConfirmRemove
        assertEquals("Tablero", dialog.boardName)
        assertEquals(emptyList<String>(), access.removals)
        viewModel.confirm()
        assertEquals(listOf(uuid(3)), access.removals)
        assertEquals(BoardRoleDisplay.SAME_AS_WORKSPACE, viewModel.uiState.value.rows.first { it.userId == uuid(3) }.boardRole)
    }

    @Test
    fun quitar_cancelar_noHacePeticion() = runTest {
        val viewModel = opened(overrides = listOf(boardOverride(3)))
        viewModel.requestRemove(uuid(3))

        viewModel.dismissDialog()
        viewModel.confirm()

        assertEquals(emptyList<String>(), access.removals)
    }

    @Test
    fun quitar_sinOverride_noOfrece() = runTest {
        val viewModel = opened()

        viewModel.requestRemove(uuid(3))

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun quitar_sinPermiso_noHacePeticion() = runTest {
        val viewModel = opened(workspaceRole = WorkspaceRole.EDITOR, selfN = 3)
        members.mutableState.value = MembersState(ws, defaultItems(3, WorkspaceRole.EDITOR), loaded = true)
        access.mutableState.value = access.mutableState.value.copy(overrides = listOf(boardOverride(4)))

        viewModel.requestRemove(uuid(4))
        viewModel.confirm()

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(emptyList<String>(), access.removals)
    }

    @Test
    fun falloDeAccion_emiteShowError() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        access.mutationResult = failed(DomainError.Network("NETWORK_ERROR", "x"))

        viewModel.setRole(WorkspaceRole.VIEWER)

        assertEquals(listOf<BoardAccessEvent>(BoardAccessEvent.ShowError(BoardRoleErrorMessage.Offline)), events)
        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertFalse(viewModel.uiState.value.submitting)
    }

    @Test
    fun errorSinDatos_muestraErrorConReintento() = runTest {
        access.readResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(BoardAccessPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(BoardRoleErrorMessage.Offline, viewModel.uiState.value.error)

        access.readResult = WorkspaceOutcome.Done
        workspaces.mutableState.value = WorkspacesState(listOf(workspace(id = ws, role = WorkspaceRole.EDITOR)), loaded = true)
        viewModel.refresh()

        assertEquals(BoardAccessPhase.READY, viewModel.uiState.value.phase)
    }

    @Test
    fun idsDeRutaNoUuid_avisaYSale() = runTest {
        val viewModel = viewModel(boardId = "../x")

        viewModel.onOpened()

        assertEquals(
            listOf(BoardAccessEvent.ShowError(BoardRoleErrorMessage.BoardGone), BoardAccessEvent.LeaveScreen),
            events,
        )
        assertEquals(emptyList<Pair<String, String>>(), access.opens)
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        opened()

        session.value = SessionState.SignedOut(expired = true)

        assertEquals(listOf<BoardAccessEvent>(BoardAccessEvent.LeaveScreen), events)
    }

    @Test
    fun sinSesion_laSenalNoHaceNada() = runTest {
        opened()
        session.value = SessionState.SignedOut()
        val before = access.refreshCalls

        signals.emit()
        runCurrent()

        assertEquals(before, access.refreshCalls)
    }

    @Test
    fun senal_refrescaElRolEfectivoSinGestoDelUsuario() = runTest {
        val viewModel = opened()
        access.effectiveRoleOnRead = WorkspaceRole.VIEWER

        signals.emit()

        assertEquals(1, access.refreshCalls)
        assertEquals(RoleLabel.VIEWER, viewModel.uiState.value.roleLabel)
        assertEquals(listOf(BoardCapability.VIEW), viewModel.uiState.value.capabilities)
    }

    @Test
    fun senal_rolCambia_avisaConElNuevoRol() = runTest {
        opened()
        access.effectiveRoleOnRead = WorkspaceRole.VIEWER

        signals.emit()

        assertEquals(
            listOf<BoardAccessEvent>(BoardAccessEvent.ShowMessage(BoardAccessNotice.RoleChanged(WorkspaceRole.VIEWER))),
            events,
        )
    }

    @Test
    fun senal_rolIgual_noAvisa() = runTest {
        opened()

        signals.emit()

        assertEquals(1, access.refreshCalls)
        assertEquals(emptyList<BoardAccessEvent>(), events)
    }

    @Test
    fun senales_enRafaga_hacenUnSoloRefrescoExtra() = runTest {
        opened()
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        signals.emit()
        repeat(3) { signals.emit() }
        runCurrent()
        assertEquals(1, access.refreshCalls)
        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS)
        runCurrent()
        advanceTimeBy(BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS * 3)
        runCurrent()

        assertEquals(2, access.refreshCalls)
    }

    @Test
    fun senal_respetaLaPausaMinima() = runTest {
        opened()

        signals.emit()
        signals.emit()
        runCurrent()
        assertEquals(1, access.refreshCalls)
        advanceTimeBy(BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS - 1)
        runCurrent()
        assertEquals(1, access.refreshCalls)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, access.refreshCalls)
    }

    @Test
    fun senal_adminPierdeGestion_ocultaElPanel() = runTest {
        val viewModel = opened()
        assertTrue(viewModel.uiState.value.showPanel)
        demoteSelfToViewer()

        signals.emit()

        assertFalse(viewModel.uiState.value.showPanel)
        assertEquals(emptyList<OverrideRowUi>(), viewModel.uiState.value.rows)
    }

    @Test
    fun senal_ganaGestion_cargaOverrides() = runTest {
        val viewModel = opened(workspaceRole = WorkspaceRole.EDITOR, selfN = 3)
        assertEquals(emptyList<String>(), members.loads)
        workspaces.refreshItems = listOf(workspace(id = ws, name = "Equipo", role = WorkspaceRole.ADMIN))
        members.loadItems = defaultItems(3, WorkspaceRole.ADMIN)
        access.overridesOnLoad = emptyList()

        signals.emit()

        assertEquals(listOf(ws), members.loads)
        assertEquals(1, access.overrideLoads)
        assertTrue(viewModel.uiState.value.showPanel)
    }

    @Test
    fun senal_conDialogoAbierto_loCierraSiYaNoEsPermitido() = runTest {
        val viewModel = opened()
        viewModel.openSetRole(uuid(3))
        assertTrue(viewModel.uiState.value.dialog is BoardDialog.SetRole)
        demoteSelfToViewer()

        signals.emit()

        assertEquals(BoardDialog.None, viewModel.uiState.value.dialog)
        assertEquals(emptyList<Pair<String, WorkspaceRole>>(), access.sets)
    }

    @Test
    fun senal_tableroDesaparecido_avisaYSale() = runTest {
        opened()
        access.readResult = api("BOARD_NOT_FOUND")

        signals.emit()

        assertEquals(
            listOf(BoardAccessEvent.ShowError(BoardRoleErrorMessage.BoardGone), BoardAccessEvent.LeaveScreen),
            events,
        )
    }

    @Test
    fun senal_fallo_conservaElEstado() = runTest {
        val viewModel = opened()
        access.readResult = failed(DomainError.Network("NETWORK_ERROR", "x"))

        signals.emit()
        runCurrent()
        advanceTimeBy(BoardRoleRules.SIGNAL_REFRESH_PAUSE_MILLIS)
        signals.emit()
        runCurrent()

        assertEquals(RoleLabel.EDITOR, viewModel.uiState.value.roleLabel)
        assertEquals(BoardAccessPhase.READY, viewModel.uiState.value.phase)
        assertEquals(listOf<BoardAccessEvent>(BoardAccessEvent.ShowError(BoardRoleErrorMessage.Offline)), events)
        assertEquals(2, access.refreshCalls)
    }

    @Test
    fun senal_429_noReintentaAntesDeRetryAfter() = runTest {
        opened()
        access.readResult = failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfter = 30)

        signals.emit()
        signals.emit()
        runCurrent()
        assertEquals(1, access.refreshCalls)
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, access.refreshCalls)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, access.refreshCalls)
    }

    @Test
    fun toStringDeLaInterfaz_noIncluyeDatosPersonales() = runTest {
        val viewModel = opened(overrides = listOf(boardOverride(3)))
        viewModel.requestRemove(uuid(3))

        val state = viewModel.uiState.value
        val text = state.toString() + state.rows.joinToString() + state.dialog.toString()

        assertFalse(text.contains("Carla") || text.contains("m3@example.com") || text.contains(uuid(3)) || text.contains("Tablero"))
    }
}
