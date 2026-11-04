package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.AppNotificationKind
import com.simposio.taskeer.domain.model.NotificationEvent
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.testing.FakeNotificationHistoryRepository
import com.simposio.taskeer.testing.FakeTaskLookupRepository
import com.simposio.taskeer.ui.notifications.NotificationsEvent
import com.simposio.taskeer.ui.notifications.NotificationsUiState
import com.simposio.taskeer.ui.notifications.NotificationsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private var now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private var zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val history = FakeNotificationHistoryRepository { now }
    private val tasks = FakeTaskLookupRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): NotificationsViewModel {
        val viewModel = NotificationsViewModel(history, tasks, DateTimeConverter(TimeZoneProvider { zone }))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun record(event: NotificationEvent, delivered: Boolean = true) {
        history.record(event, delivered)
        now = now.plusSeconds(60)
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
        sync = SyncMetadata(clientId = id, updatedAt = Instant.EPOCH, pending = false, deletedAt = null),
    )

    private suspend fun NotificationsViewModel.firstEvent(): NotificationsEvent? =
        withTimeoutOrNull(50) { events.first() }

    @Test
    fun sinEntradas_estadoVacio() = runTest {
        val state = viewModel().uiState.value

        assertTrue(state.rows.isEmpty())
        assertEquals(NotificationsUiState(), state)
    }

    @Test
    fun filas_ordenadasDeMasRecienteAAntigua() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Deadline("t1", "Segunda"))
        record(NotificationEvent.Manual("r1", "t2", "Tercera"))

        assertEquals(listOf("Tercera", "Segunda", null), viewModel.uiState.value.rows.map { it.taskTitle })
    }

    @Test
    fun fecha_seFormateaEnLaZonaVigente() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        val created = Instant.parse("2026-11-04T10:00:00Z")

        val madrid = viewModel.uiState.value.rows.single().createdAtText
        zone = ZoneId.of("Asia/Tokyo")
        val tokyo = NotificationsViewModel(history, tasks, DateTimeConverter(TimeZoneProvider { zone }))
            .also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} } }
            .uiState.value.rows.single().createdAtText

        assertEquals(created.atZone(ZoneId.of("Europe/Madrid")).toLocalDateTime().format(formatter), madrid)
        assertEquals(created.atZone(ZoneId.of("Asia/Tokyo")).toLocalDateTime().format(formatter), tokyo)
    }

    @Test
    fun fila_marcaNoLeidaYNoEntregada() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Deadline("t1", "A"), delivered = false)

        val row = viewModel.uiState.value.rows.single()

        assertTrue(row.unread)
        assertFalse(row.delivered)
        assertEquals(1, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun resumen_conservaTipoYConteo() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(5))

        val row = viewModel.uiState.value.rows.single()

        assertEquals(AppNotificationKind.DIGEST, row.kind)
        assertEquals(5, row.activeTaskCount)
        assertNull(row.taskId)
    }

    @Test
    fun markRead_quitaLaMarcaYDescuentaElContador() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Digest(2))
        val id = viewModel.uiState.value.rows.last().id

        viewModel.markRead(id)

        assertFalse(viewModel.uiState.value.rows.last().unread)
        assertEquals(1, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun markAllRead_dejaElContadorACero() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Digest(2))

        viewModel.markAllRead()

        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertTrue(viewModel.uiState.value.rows.none { it.unread })
    }

    @Test
    fun delete_quitaLaFilaYDescuentaElContadorSiEstabaSinLeer() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Digest(2))
        val id = viewModel.uiState.value.rows.first().id

        viewModel.delete(id)

        assertEquals(1, viewModel.uiState.value.rows.size)
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertNull(viewModel.firstEvent())
    }

    @Test
    fun borrarTodo_pideConfirmacionYNoBorraAlCancelar() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Digest(2))

        viewModel.requestDeleteAll()
        assertTrue(viewModel.uiState.value.confirmDeleteAll)
        assertEquals(2, viewModel.uiState.value.rows.size)

        viewModel.cancelDeleteAll()

        assertFalse(viewModel.uiState.value.confirmDeleteAll)
        assertEquals(2, viewModel.uiState.value.rows.size)
    }

    @Test
    fun borrarTodo_confirmado_vaciaListaYContador() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        record(NotificationEvent.Digest(2))
        viewModel.markRead(viewModel.uiState.value.rows.first().id)

        viewModel.requestDeleteAll()
        viewModel.confirmDeleteAll()

        assertTrue(viewModel.uiState.value.rows.isEmpty())
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertFalse(viewModel.uiState.value.confirmDeleteAll)
    }

    @Test
    fun borrarTodo_conListaVacia_noDisponible() = runTest {
        val viewModel = viewModel()

        viewModel.requestDeleteAll()

        assertFalse(viewModel.uiState.value.confirmDeleteAll)
    }

    @Test
    fun confirmacion_sobreviveARecreacion() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(1))
        viewModel.requestDeleteAll()

        val afterRecreation = withTimeoutOrNull(50) { viewModel.uiState.first { it.confirmDeleteAll } }

        assertTrue(afterRecreation != null)
        assertEquals(1, afterRecreation?.rows?.size)
    }

    @Test
    fun tocarEntradaConTarea_marcaLeidaYEmiteOpenTask() = runTest {
        tasks.existing["t1"] = task("t1")
        val viewModel = viewModel()
        record(NotificationEvent.Deadline("t1", "A"))
        val row = viewModel.uiState.value.rows.single()

        viewModel.onOpen(row)

        assertFalse(viewModel.uiState.value.rows.single().unread)
        assertEquals(NotificationsEvent.OpenTask("t1"), viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaConTareaBorrada_marcaLeidaYEmiteShowTaskGoneUnaVez() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Manual("r1", "t9", "Borrada"))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertFalse(viewModel.uiState.value.rows.single().unread)
        assertEquals(NotificationsEvent.ShowTaskGone, viewModel.firstEvent())
        assertNull(viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaConTareaInexistente_noEmiteOpenTaskNiFalla() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Deadline("nunca-existio", "Fantasma"))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(NotificationsEvent.ShowTaskGone, viewModel.firstEvent())
        assertEquals(0, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun tocarResumen_soloMarcaLeida() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Digest(3))

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertFalse(viewModel.uiState.value.rows.single().unread)
        assertNull(viewModel.firstEvent())
    }

    @Test
    fun tocarEntradaYaLeida_siguiendoAbreLaTarea() = runTest {
        tasks.existing["t1"] = task("t1")
        val viewModel = viewModel()
        record(NotificationEvent.Deadline("t1", "A"))
        viewModel.markAllRead()

        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(NotificationsEvent.OpenTask("t1"), viewModel.firstEvent())
    }

    @Test
    fun showTaskGone_noSeReemiteTrasRecolectarDeNuevo() = runTest {
        val viewModel = viewModel()
        record(NotificationEvent.Deadline("t9", "Borrada"))
        viewModel.onOpen(viewModel.uiState.value.rows.single())

        assertEquals(NotificationsEvent.ShowTaskGone, viewModel.firstEvent())

        assertNull(viewModel.firstEvent())
    }
}
