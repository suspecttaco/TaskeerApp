package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.auth.AccountSummary
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.notification.InboxOutcome
import com.simposio.taskeer.domain.notification.InboxState
import com.simposio.taskeer.domain.notification.ServerNotification
import com.simposio.taskeer.domain.notification.ServerNotificationType
import com.simposio.taskeer.testing.FakeNotificationInboxRepository
import com.simposio.taskeer.testing.FakeTaskLookupRepository
import java.time.Instant
import java.time.ZoneId
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
class AccountNotificationsViewModelTest {

    private val inbox = FakeNotificationInboxRepository()
    private val tasks = FakeTaskLookupRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(AccountSummary("a@x.com", "A"), offline = false))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): AccountNotificationsViewModel {
        val viewModel = AccountNotificationsViewModel(
            inbox, session, tasks, DateTimeConverter(TimeZoneProvider { ZoneId.of("UTC") }),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun item(id: String, taskId: String? = null, read: Boolean = false) = ServerNotification(
        id = id,
        type = ServerNotificationType.TASK_ASSIGNED,
        title = "Aviso $id",
        body = "Cuerpo $id",
        taskId = taskId,
        createdAt = Instant.parse("2026-11-26T09:00:00Z"),
        readAt = if (read) Instant.parse("2026-11-26T09:30:00Z") else null,
    )

    private fun loaded(vararg items: ServerNotification) {
        inbox.mutableState.value = InboxState(items.toList(), items.count { it.isUnread }, listLoaded = true)
    }

    private fun task(id: String) = Task(
        columnId = "c1",
        title = "Tarea $id",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        sync = SyncMetadata(clientId = id, updatedAt = Instant.EPOCH, pending = false, deletedAt = null),
    )

    private suspend fun AccountNotificationsViewModel.firstEvent(): AccountNotificationsEvent? =
        withTimeoutOrNull(50) { events.first() }

    @Test
    fun sinSesion_noHayPestanaCuenta() = runTest {
        session.value = SessionState.SignedOut()

        assertEquals(listOf(NotificationsTab.DEVICE), viewModel().uiState.value.tabs)
    }

    @Test
    fun conSesion_hayPestanaCuenta() = runTest {
        assertEquals(listOf(NotificationsTab.DEVICE, NotificationsTab.ACCOUNT), viewModel().uiState.value.tabs)
    }

    @Test
    fun primerRefresco_muestraCargandoYLuegoLista() = runTest {
        val viewModel = viewModel()
        inbox.refreshItems = listOf(item("1"), item("2", read = true))
        assertEquals(AccountPhase.LOADING, viewModel.uiState.value.phase)

        viewModel.onTabOpened()

        val state = viewModel.uiState.value
        assertEquals(AccountPhase.LIST, state.phase)
        assertEquals(2, state.rows.size)
        assertEquals(1, state.unreadCount)
        assertEquals(1, state.readCount)
    }

    @Test
    fun onTabOpened_soloRefrescaLaPrimeraVez() = runTest {
        val viewModel = viewModel()
        inbox.refreshItems = listOf(item("1"))

        viewModel.onTabOpened()
        viewModel.onTabOpened()

        assertEquals(1, inbox.refreshCalls)
    }

    @Test
    fun listaVacia_muestraVacio() = runTest {
        val viewModel = viewModel()
        inbox.refreshItems = emptyList()

        viewModel.onTabOpened()

        assertEquals(AccountPhase.EMPTY, viewModel.uiState.value.phase)
    }

    @Test
    fun errorSinLista_muestraErrorConReintento() = runTest {
        val viewModel = viewModel()
        inbox.result = InboxOutcome.Failed(DomainError.Network("NETWORK_ERROR", "x"))

        viewModel.onTabOpened()

        assertEquals(AccountPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(InboxErrorMessage.Offline, viewModel.uiState.value.error)
        inbox.result = InboxOutcome.Done
        inbox.refreshItems = listOf(item("1"))
        viewModel.refresh()
        assertEquals(AccountPhase.LIST, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun errorConLista_conservaLaListaYAvisa() = runTest {
        val viewModel = viewModel()
        loaded(item("1"))
        inbox.result = InboxOutcome.Failed(DomainError.Server("INTERNAL_SERVER_ERROR", "x"))

        viewModel.refresh()

        assertEquals(AccountPhase.LIST, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals(AccountNotificationsEvent.ShowError(InboxErrorMessage.ServerDown), viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaConTarea_marcaLeidaYAbreElEditor() = runTest {
        val viewModel = viewModel()
        loaded(item("n1", taskId = "t1"))
        tasks.existing["t1"] = task("t1")

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(listOf("n1"), inbox.markedRead)
        assertEquals(AccountNotificationsEvent.OpenTask("t1"), viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaConTareaAusente_avisa() = runTest {
        val viewModel = viewModel()
        loaded(item("n1", taskId = "t1"))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(listOf("n1"), inbox.markedRead)
        assertEquals(AccountNotificationsEvent.ShowTaskGone, viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaSinTarea_soloMarcaLeida() = runTest {
        val viewModel = viewModel()
        loaded(item("n1"))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(listOf("n1"), inbox.markedRead)
        assertNull(viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaYaLeida_noLlamaAMarcar() = runTest {
        val viewModel = viewModel()
        loaded(item("n1", read = true))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertTrue(inbox.markedRead.isEmpty())
    }

    @Test
    fun borrarLeidas_exigeConfirmacion() = runTest {
        val viewModel = viewModel()
        loaded(item("1"), item("2", read = true))

        viewModel.requestDeleteRead()

        assertTrue(viewModel.uiState.value.confirmDeleteRead)
        assertEquals(0, inbox.deleteReadCalls)
        viewModel.confirmDeleteRead()
        assertEquals(1, inbox.deleteReadCalls)
        assertEquals(false, viewModel.uiState.value.confirmDeleteRead)
    }

    @Test
    fun borrarLeidas_sinLeidas_noPideConfirmacion() = runTest {
        val viewModel = viewModel()
        loaded(item("1"))

        viewModel.requestDeleteRead()

        assertEquals(false, viewModel.uiState.value.confirmDeleteRead)
    }

    @Test
    fun cancelarConfirmacion_noHacePeticion() = runTest {
        val viewModel = viewModel()
        loaded(item("2", read = true))
        viewModel.requestDeleteRead()

        viewModel.cancelDeleteRead()

        assertEquals(0, inbox.deleteReadCalls)
        assertEquals(false, viewModel.uiState.value.confirmDeleteRead)
    }

    @Test
    fun falloDeAccion_emiteShowError() = runTest {
        val viewModel = viewModel()
        loaded(item("1"))
        inbox.result = InboxOutcome.Failed(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retryAfterSeconds = 60)

        viewModel.delete("1")

        assertEquals(AccountNotificationsEvent.ShowError(InboxErrorMessage.RateLimited(1)), viewModel.firstEvent())
        assertEquals(listOf("1"), inbox.deleted)
    }

    @Test
    fun accionesDeUnaEntradaYMasivas_llamanAlRepositorio() = runTest {
        val viewModel = viewModel()
        loaded(item("1"))

        viewModel.markRead("1")
        viewModel.markAllRead()

        assertEquals(listOf("1"), inbox.markedRead)
        assertEquals(1, inbox.markAllReadCalls)
    }
}
