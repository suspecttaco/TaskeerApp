package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.workspace.AcceptOutcome
import com.simposio.taskeer.domain.workspace.InvitationStatus
import com.simposio.taskeer.domain.workspace.WorkspaceOutcome
import com.simposio.taskeer.testing.FakeMyInvitationsRepository
import com.simposio.taskeer.testing.FakeWorkspaceRepository
import com.simposio.taskeer.testing.myInvitation
import com.simposio.taskeer.testing.uuid
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
class MyInvitationsViewModelTest {

    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val inbox = FakeMyInvitationsRepository()
    private val workspaces = FakeWorkspaceRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(AccountSummary("yo@example.com", "Yo"), offline = false))
    private val events = mutableListOf<InboxEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): MyInvitationsViewModel {
        val viewModel = MyInvitationsViewModel(inbox, workspaces, session) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun TestScope.opened(): MyInvitationsViewModel {
        inbox.refreshItems = listOf(myInvitation(1, "Diseño"), myInvitation(2, "Marketing"))
        return viewModel().also { it.onOpened() }
    }

    private fun failed(error: DomainError) = WorkspaceOutcome.Failed(error)

    private fun acceptFailed(code: String) = AcceptOutcome.Failed(DomainError.fromApi(code, "x"))

    @Test
    fun primerRefresh_muestraCargandoYLuegoLista() = runTest {
        inbox.refreshItems = listOf(myInvitation(1), myInvitation(2))
        val gate = CompletableDeferred<Unit>()
        inbox.refreshGate = gate
        val viewModel = viewModel()
        assertEquals(InboxPhase.LOADING, viewModel.uiState.value.phase)

        viewModel.onOpened()
        assertEquals(InboxPhase.LOADING, viewModel.uiState.value.phase)
        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(InboxPhase.LIST, state.phase)
        assertEquals(listOf("Equipo 2", "Equipo 1"), state.rows.map { it.workspaceName })
    }

    @Test
    fun vacio_muestraMensaje() = runTest {
        inbox.refreshItems = emptyList()
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(InboxPhase.EMPTY, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.rows.isEmpty())
    }

    @Test
    fun errorSinLista_muestraErrorConReintento() = runTest {
        inbox.refreshResult = failed(DomainError.Network("NETWORK_ERROR", "x"))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(InboxPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(WorkspaceInvitationErrorMessage.Offline, viewModel.uiState.value.error)

        inbox.refreshResult = WorkspaceOutcome.Done
        inbox.refreshItems = listOf(myInvitation(1))
        viewModel.refresh()

        assertEquals(InboxPhase.LIST, viewModel.uiState.value.phase)
    }

    @Test
    fun errorConLista_conservaLaListaYAvisa() = runTest {
        val viewModel = opened()
        inbox.refreshResult = failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.refresh()

        assertEquals(InboxPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(2, viewModel.uiState.value.rows.size)
        assertEquals(listOf<InboxEvent>(InboxEvent.ShowError(WorkspaceInvitationErrorMessage.ServerDown)), events)
    }

    @Test
    fun vencida_noOfreceAceptar() = runTest {
        inbox.refreshItems = listOf(myInvitation(1, expiresAt = Instant.parse("2026-11-01T10:00:00Z")), myInvitation(2))
        val viewModel = viewModel()
        viewModel.onOpened()

        val rows = viewModel.uiState.value.rows.associateBy { it.id }

        assertEquals(false, rows.getValue(uuid(601)).canAccept)
        assertEquals(true, rows.getValue(uuid(601)).expired)
        assertEquals(true, rows.getValue(uuid(602)).canAccept)

        viewModel.accept(uuid(601))
        assertTrue(inbox.accepted.isEmpty())
    }

    @Test
    fun aceptar_conExito_refrescaWorkspacesYAvisa() = runTest {
        val viewModel = opened()

        viewModel.accept(uuid(601))

        assertEquals(listOf(uuid(601)), inbox.accepted)
        assertEquals(1, workspaces.refreshCalls)
        assertEquals(listOf(uuid(602)), viewModel.uiState.value.rows.map { it.id })
        assertEquals(listOf<InboxEvent>(InboxEvent.ShowMessage(InboxNotice.Joined("Equipo"))), events)
    }

    @Test
    fun aceptar_alreadyMember_avisaDistinto() = runTest {
        val viewModel = opened()
        inbox.acceptResult = AcceptOutcome.Accepted("Equipo", alreadyMember = true)

        viewModel.accept(uuid(601))

        assertEquals(listOf<InboxEvent>(InboxEvent.ShowMessage(InboxNotice.AlreadyMember("Equipo"))), events)
    }

    @Test
    fun aceptar_fallo_noRefrescaWorkspacesNiInsertaNada() = runTest {
        val viewModel = opened()
        inbox.acceptResult = AcceptOutcome.Failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.accept(uuid(601))

        assertEquals(0, workspaces.refreshCalls)
        assertEquals(2, viewModel.uiState.value.rows.size)
        assertEquals(listOf<InboxEvent>(InboxEvent.ShowError(WorkspaceInvitationErrorMessage.ServerDown)), events)
        assertTrue(workspaces.mutableState.value.items.isEmpty())
    }

    @Test
    fun aceptar_refrescoDeWorkspacesFalla_siguesiendoExito() = runTest {
        val viewModel = opened()
        workspaces.refreshResult = failed(DomainError.Network("NETWORK_ERROR", "x"))

        viewModel.accept(uuid(601))

        assertEquals(listOf<InboxEvent>(InboxEvent.ShowMessage(InboxNotice.Joined("Equipo"))), events)
        assertEquals(1, workspaces.refreshCalls)
    }

    @Test
    fun aceptar_invalidaOVencida_avisaYRelee() = runTest {
        val viewModel = opened()
        inbox.acceptResult = acceptFailed("INVALID_OR_EXPIRED_INVITATION")
        val callsBefore = inbox.refreshCalls

        viewModel.accept(uuid(601))

        assertEquals(listOf<InboxEvent>(InboxEvent.ShowError(WorkspaceInvitationErrorMessage.InvalidInvitation)), events)
        assertEquals(callsBefore + 1, inbox.refreshCalls)
    }

    @Test
    fun aceptar_invalida_noRefrescaWorkspaces() = runTest {
        val viewModel = opened()
        inbox.acceptResult = acceptFailed("INVITATION_NOT_FOUND")

        viewModel.accept(uuid(601))

        assertEquals(0, workspaces.refreshCalls)
    }

    @Test
    fun aceptarConEnlace_urlCompleta_extraeElToken() = runTest {
        val viewModel = opened()
        viewModel.openAcceptWithLink()

        viewModel.acceptWithLink("https://taskeer.example/invitaciones/aceptar?token=abc123")

        assertEquals(listOf("abc123"), inbox.tokens)
    }

    @Test
    fun aceptarConEnlace_tokenSuelto_seEnvia() = runTest {
        val viewModel = opened()
        viewModel.openAcceptWithLink()

        viewModel.acceptWithLink("  abc123  ")

        assertEquals(listOf("abc123"), inbox.tokens)
    }

    @Test
    fun aceptarConEnlace_vacioOMalformado_noHacePeticion() = runTest {
        val viewModel = opened()
        viewModel.openAcceptWithLink()

        viewModel.acceptWithLink("   ")
        viewModel.acceptWithLink("dos palabras")

        assertTrue(inbox.tokens.isEmpty())
        assertEquals(WorkspaceInvitationErrorMessage.InvalidLink, viewModel.uiState.value.dialogError)
        assertEquals(InboxDialog.ACCEPT_WITH_LINK, viewModel.uiState.value.dialog)
    }

    @Test
    fun aceptarConEnlace_conExito_refrescaWorkspacesYAvisa() = runTest {
        val viewModel = opened()
        viewModel.openAcceptWithLink()

        viewModel.acceptWithLink("abc123")

        assertEquals(InboxDialog.NONE, viewModel.uiState.value.dialog)
        assertEquals(1, workspaces.refreshCalls)
        assertEquals(listOf<InboxEvent>(InboxEvent.ShowMessage(InboxNotice.Joined("Equipo"))), events)
        assertEquals(2, inbox.refreshCalls)
    }

    @Test
    fun aceptarConEnlace_invalido_avisaSinTocarNada() = runTest {
        val viewModel = opened()
        viewModel.openAcceptWithLink()
        inbox.acceptResult = acceptFailed("INVALID_OR_EXPIRED_INVITATION")

        viewModel.acceptWithLink("abc123")

        assertEquals(WorkspaceInvitationErrorMessage.InvalidInvitation, viewModel.uiState.value.dialogError)
        assertEquals(0, workspaces.refreshCalls)
        assertEquals(2, viewModel.uiState.value.rows.size)
        assertEquals(1, inbox.refreshCalls)
    }

    @Test
    fun invitacionConEstadoDesconocido_noSeMuestra() = runTest {
        inbox.refreshItems = listOf(myInvitation(1, status = InvitationStatus.UNKNOWN), myInvitation(2))
        val viewModel = viewModel()

        viewModel.onOpened()

        assertEquals(listOf(uuid(602)), viewModel.uiState.value.rows.map { it.id })
    }

    @Test
    fun cerrarSesion_emiteSalida() = runTest {
        viewModel()

        session.value = SessionState.SignedOut()

        assertEquals(listOf<InboxEvent>(InboxEvent.LeaveScreen), events)
    }
}
