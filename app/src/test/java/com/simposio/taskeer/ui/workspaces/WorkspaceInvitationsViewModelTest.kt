package com.simposio.taskeer.ui.workspaces

import androidx.lifecycle.SavedStateHandle
import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.MembersState
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.domain.workspace.WorkspaceRole
import com.simposio.taskeer.domain.workspace.WorkspacesState
import com.simposio.taskeer.testing.FakeWorkspaceInvitationsRepository
import com.simposio.taskeer.testing.FakeWorkspaceMembersRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.invitation
import com.simposio.taskeer.testing.member
import com.simposio.taskeer.testing.uuid
import com.simposio.taskeer.testing.workspace
import java.time.Instant
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
class WorkspaceInvitationsViewModelTest {

    private val ws = uuid(100)
    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val invitations = FakeWorkspaceInvitationsRepository()
    private val members = FakeWorkspaceMembersRepository()
    private val workspaces = FakeWorkspaceRepository()
    private val session = MutableStateFlow<SessionState>(signedIn(1))
    private val events = mutableListOf<InvitationEvent>()

    private fun signedIn(n: Int, verified: Boolean? = true): SessionState =
        SessionState.SignedIn(AccountSummary("m$n@example.com", "Yo", emailVerified = verified), offline = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun roles() = listOf(
        member(1, WorkspaceRole.OWNER),
        member(2, WorkspaceRole.ADMIN),
        member(3, WorkspaceRole.EDITOR),
        member(4, WorkspaceRole.VIEWER),
    )

    private fun TestScope.viewModel(): WorkspaceInvitationsViewModel {
        val viewModel = WorkspaceInvitationsViewModel(
            SavedStateHandle(mapOf(WorkspaceMembersViewModel.WORKSPACE_ID_ARG to ws)),
            invitations,
            members,
            workspaces,
            session,
        ) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun TestScope.ready(
        selfN: Int = 1,
        personal: Boolean = false,
        verified: Boolean? = true,
        loadList: Boolean = true,
    ): WorkspaceInvitationsViewModel {
        val role = roles().first { it.userId == uuid(selfN) }.role
        workspaces.mutableState.value =
            WorkspacesState(listOf(workspace(id = ws, name = "Equipo", role = role, isPersonal = personal)), loaded = true)
        session.value = signedIn(selfN, verified)
        if (loadList) invitations.loadItems = listOf(invitation(1), invitation(2, expiresAt = Instant.parse("2026-11-01T10:00:00Z")))
        val viewModel = viewModel()
        members.mutableState.value = MembersState(ws, roles(), loaded = true)
        return viewModel
    }

    private fun failed(error: DomainError) = WorkspaceOutcome.Failed(error)

    private fun api(code: String) = failed(DomainError.fromApi(code, "x"))

    @Test
    fun cargaAlEstarLosMiembros_yMuestraLaLista() = runTest {
        val viewModel = ready()

        val state = viewModel.uiState.value
        assertEquals(listOf(ws), invitations.loads)
        assertEquals(SectionVisibility.LIST, state.section)
        assertEquals(listOf(uuid(501), uuid(502)), state.rows.map { it.id })
        assertEquals(listOf(false, true), state.rows.map { it.expired })
        assertTrue(state.canInvite)
    }

    @Test
    fun noCargaInvitaciones_siElRolNoEsGestor() = runTest {
        val viewModel = ready(selfN = 3)

        assertTrue(invitations.loads.isEmpty())
        assertEquals(SectionVisibility.HIDDEN, viewModel.uiState.value.section)
        assertEquals(false, viewModel.uiState.value.canInvite)
    }

    @Test
    fun noCargaInvitaciones_enUnWorkspacePersonal() = runTest {
        val viewModel = ready(personal = true)

        assertTrue(invitations.loads.isEmpty())
        assertEquals(SectionVisibility.HIDDEN, viewModel.uiState.value.section)
    }

    @Test
    fun load_conErrorConservaLosMiembros() = runTest {
        invitations.loadResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))
        val viewModel = ready(loadList = false)

        assertEquals(SectionVisibility.ERROR, viewModel.uiState.value.section)
        assertEquals(WorkspaceInvitationErrorMessage.ServerDown, viewModel.uiState.value.error)
        assertEquals(4, members.mutableState.value.items.size)
        assertTrue(members.loads.isEmpty())

        invitations.loadResult = WorkspaceOutcome.Done
        invitations.loadItems = listOf(invitation(1))
        viewModel.refresh()

        assertEquals(SectionVisibility.LIST, viewModel.uiState.value.section)
    }

    @Test
    fun load_403_ocultaLaSeccionYRelee() = runTest {
        invitations.loadResult = api("FORBIDDEN")
        val viewModel = ready(loadList = false)

        assertEquals(SectionVisibility.HIDDEN, viewModel.uiState.value.section)
        assertEquals(listOf<InvitationEvent>(InvitationEvent.ShowError(WorkspaceInvitationErrorMessage.NoPermission)), events)
        assertEquals(listOf(ws), members.loads)
        assertEquals(1, workspaces.refreshCalls)
    }

    @Test
    fun invitar_sinPermiso_noHacePeticion() = runTest {
        val viewModel = ready(selfN = 3)

        viewModel.openInvite()
        viewModel.invite("nuevo@example.com", WorkspaceRole.VIEWER)

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertTrue(invitations.invites.isEmpty())
    }

    @Test
    fun invitar_correoInvalido_noHacePeticion() = runTest {
        val viewModel = ready()
        viewModel.openInvite()

        viewModel.invite("no-es-correo", WorkspaceRole.VIEWER)

        assertTrue(invitations.invites.isEmpty())
        assertEquals(WorkspaceInvitationErrorMessage.InvalidEmail, viewModel.uiState.value.dialogError)
        assertTrue(viewModel.uiState.value.dialog is InvitationDialog.Invite)
    }

    @Test
    fun invitar_yaMiembro_noHacePeticion() = runTest {
        val viewModel = ready()
        viewModel.openInvite()

        viewModel.invite("M3@example.com", WorkspaceRole.VIEWER)

        assertTrue(invitations.invites.isEmpty())
        assertEquals(WorkspaceInvitationErrorMessage.AlreadyMember, viewModel.uiState.value.dialogError)
    }

    @Test
    fun invitar_yaInvitado_noHacePeticion() = runTest {
        val viewModel = ready()
        viewModel.openInvite()

        viewModel.invite("i1@example.com", WorkspaceRole.VIEWER)

        assertTrue(invitations.invites.isEmpty())
        assertEquals(WorkspaceInvitationErrorMessage.AlreadyInvited, viewModel.uiState.value.dialogError)
    }

    @Test
    fun invitar_correoSinVerificar_noHacePeticion() = runTest {
        val viewModel = ready(verified = false)

        viewModel.openInvite()
        viewModel.invite("nuevo@example.com", WorkspaceRole.VIEWER)

        assertTrue(viewModel.uiState.value.dialog is InvitationDialog.VerifyEmail)
        assertTrue(invitations.invites.isEmpty())
    }

    @Test
    fun invitar_correoConVerificacionDesconocida_permiteEnviar() = runTest {
        val viewModel = ready(verified = null)

        viewModel.openInvite()

        assertTrue(viewModel.uiState.value.dialog is InvitationDialog.Invite)
    }

    @Test
    fun invitar_conExito_cierraElDialogoYAvisa() = runTest {
        val viewModel = ready()
        viewModel.openInvite()

        viewModel.invite("  nuevo@example.com ", WorkspaceRole.EDITOR)

        assertEquals(listOf("nuevo@example.com" to WorkspaceRole.EDITOR), invitations.invites)
        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertEquals(false, viewModel.uiState.value.submitting)
        assertEquals(listOf<InvitationEvent>(InvitationEvent.ShowMessage(InvitationNotice.Invited("nuevo@example.com"))), events)
    }

    @Test
    fun invitar_dobleToque_envia_unaSolaVez() = runTest {
        val viewModel = ready()
        viewModel.openInvite()
        val gate = CompletableDeferred<Unit>()
        invitations.inviteGate = gate

        viewModel.invite("nuevo@example.com", WorkspaceRole.VIEWER)
        viewModel.invite("nuevo@example.com", WorkspaceRole.VIEWER)
        gate.complete(Unit)

        assertEquals(1, invitations.invites.size)
    }

    @Test
    fun invitar_fallo_conservaElDialogoYElTexto() = runTest {
        val viewModel = ready()
        viewModel.openInvite()
        invitations.inviteResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.invite("nuevo@example.com", WorkspaceRole.ADMIN)

        val dialog = viewModel.uiState.value.dialog as InvitationDialog.Invite
        assertEquals("nuevo@example.com", dialog.email)
        assertEquals(WorkspaceRole.ADMIN, dialog.role)
        assertEquals(WorkspaceInvitationErrorMessage.ServerDown, viewModel.uiState.value.dialogError)
        assertEquals(false, viewModel.uiState.value.submitting)
    }

    @Test
    fun invitar_emailNotVerified_muestraVerificarYConservaElTexto() = runTest {
        val viewModel = ready(verified = null)
        viewModel.openInvite()
        invitations.inviteResult = api("EMAIL_NOT_VERIFIED")

        viewModel.invite("nuevo@example.com", WorkspaceRole.ADMIN)

        assertEquals(InvitationDialog.VerifyEmail("nuevo@example.com", WorkspaceRole.ADMIN), viewModel.uiState.value.dialog)
        assertEquals(1, invitations.invites.size)

        viewModel.verifyEmail()

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertEquals(listOf<InvitationEvent>(InvitationEvent.OpenVerifyEmail), events)
    }

    @Test
    fun invitar_403Forbidden_avisaYRecarga() = runTest {
        val viewModel = ready()
        viewModel.openInvite()
        invitations.inviteResult = api("FORBIDDEN")

        viewModel.invite("nuevo@example.com", WorkspaceRole.VIEWER)

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertEquals(listOf<InvitationEvent>(InvitationEvent.ShowError(WorkspaceInvitationErrorMessage.NoPermission)), events)
        assertEquals(listOf(ws), members.loads)
        assertEquals(1, workspaces.refreshCalls)
    }

    @Test
    fun revocar_exigeConfirmacion() = runTest {
        val viewModel = ready()

        viewModel.requestRevoke(uuid(501))

        assertTrue(viewModel.uiState.value.dialog is InvitationDialog.ConfirmRevoke)
        assertTrue(invitations.revocations.isEmpty())
    }

    @Test
    fun cancelarConfirmacion_noHacePeticion() = runTest {
        val viewModel = ready()
        viewModel.requestRevoke(uuid(501))

        viewModel.dismissDialog()

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertTrue(invitations.revocations.isEmpty())
    }

    @Test
    fun revocar_sinPermiso_noHacePeticion() = runTest {
        val viewModel = ready(selfN = 3)
        invitations.mutableState.value = invitations.mutableState.value.copy(workspaceId = ws, items = listOf(invitation(1)), loaded = true)

        viewModel.requestRevoke(uuid(501))
        viewModel.confirmRevoke()

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
        assertTrue(invitations.revocations.isEmpty())
    }

    @Test
    fun revocar_vencida_noPideConfirmacion() = runTest {
        val viewModel = ready()

        viewModel.requestRevoke(uuid(502))

        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun revocar_conExito_quitaLaFila() = runTest {
        val viewModel = ready()
        viewModel.requestRevoke(uuid(501))

        viewModel.confirmRevoke()

        assertEquals(listOf(uuid(501)), invitations.revocations)
        assertEquals(listOf(uuid(502)), viewModel.uiState.value.rows.map { it.id })
        assertEquals(InvitationDialog.None, viewModel.uiState.value.dialog)
    }

    @Test
    fun revocar_workspaceDesaparecido_avisaYSale() = runTest {
        val viewModel = ready()
        invitations.revokeResult = api("WORKSPACE_NOT_FOUND")
        viewModel.requestRevoke(uuid(501))

        viewModel.confirmRevoke()

        assertEquals(
            listOf(InvitationEvent.ShowError(WorkspaceInvitationErrorMessage.WorkspaceGone), InvitationEvent.LeaveScreen),
            events,
        )
        assertEquals(1, workspaces.refreshCalls)
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        ready()

        session.value = SessionState.SignedOut()

        assertEquals(listOf<InvitationEvent>(InvitationEvent.LeaveScreen), events)
    }
}
