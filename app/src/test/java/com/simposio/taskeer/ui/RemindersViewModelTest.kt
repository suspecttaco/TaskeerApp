package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.ui.tasks.RemindersUiState
import com.simposio.taskeer.ui.tasks.RemindersViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RemindersViewModelTest {

    private val zone: ZoneId = ZoneId.of("America/Mexico_City")
    private val converter = DateTimeConverter { zone }
    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeReminderRepository) =
        RemindersViewModel(repository, preferences, { zone }, converter) { now }

    @Test
    fun laterToday_creaElRecordatorioCincoHorasDespuesDeAhora() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.laterToday()

        assertEquals(listOf("t1" to Instant.parse("2026-11-04T15:00:00Z")), repository.created)
    }

    @Test
    fun tomorrow_usaLaHoraPorDefectoYLaZonaInyectada() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.tomorrow()

        assertEquals(Instant.parse("2026-11-05T15:00:00Z"), repository.created.single().second)
    }

    @Test
    fun nextWeek_esHoyMasSieteDiasALaHoraPorDefecto() = runTest {
        val repository = FakeReminderRepository(now)
        preferences.setDefaultReminderTime(LocalTime.of(8, 30))
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.nextWeek()

        assertEquals(Instant.parse("2026-11-11T14:30:00Z"), repository.created.single().second)
    }

    @Test
    fun cambiarLaHoraPorDefectoDespues_noModificaElRemindAtYaCreado() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository).apply { open("t1") }
        viewModel.tomorrow()
        val created = Instant.parse("2026-11-05T15:00:00Z")

        preferences.setDefaultReminderTime(LocalTime.of(8, 30))

        assertEquals(listOf("t1" to created), repository.created)
        assertEquals(created, viewModel.uiState.value.reminder?.remindAt)
    }

    @Test
    fun custom_convierteLaHoraLocalAUtcConLaZonaInyectada() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.custom(LocalDateTime.of(2026, 11, 5, 9, 30))

        assertEquals(Instant.parse("2026-11-05T15:30:00Z"), repository.created.single().second)
    }

    @Test
    fun crear_conErrorDeValidacion_muestraElMensajeEnEspanol() = runTest {
        val repository = FakeReminderRepository(now).apply {
            failure = DomainError.Validation("VALIDATION_ERROR", "La hora del recordatorio debe ser futura")
        }
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.laterToday()

        assertEquals("La hora del recordatorio debe ser futura", viewModel.uiState.value.error)
    }

    @Test
    fun crear_conConflicto_muestraElMensajeEnEspanol() = runTest {
        val repository = FakeReminderRepository(now).apply {
            failure = DomainError.Conflict(
                "REMINDER_ALREADY_PENDING",
                "La tarea ya tiene un recordatorio pendiente",
            )
        }
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.tomorrow()

        assertEquals("La tarea ya tiene un recordatorio pendiente", viewModel.uiState.value.error)
    }

    @Test
    fun crearConExito_limpiaElErrorAnterior() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository).apply { open("t1") }
        repository.failure = DomainError.Validation("VALIDATION_ERROR", "mal")
        viewModel.laterToday()
        repository.failure = null

        viewModel.laterToday()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun open_muestraElPendienteConSuHoraLocal() = runTest {
        val repository = FakeReminderRepository(now)
        repository.put(newReminder("t1", Instant.parse("2026-11-05T15:30:00Z"), now, clientId = "r1"))
        val viewModel = viewModel(repository)

        viewModel.open("t1")

        assertEquals("r1", viewModel.uiState.value.reminder?.id)
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 30), viewModel.uiState.value.reminderAtLocal)
    }

    @Test
    fun cancel_limpiaElEstadoViaObserveReminder() = runTest {
        val repository = FakeReminderRepository(now)
        repository.put(newReminder("t1", now.plusSeconds(60), now, clientId = "r1"))
        val viewModel = viewModel(repository).apply { open("t1") }

        viewModel.cancel()

        assertNull(viewModel.uiState.value.reminder)
        assertNull(viewModel.uiState.value.reminderAtLocal)
        assertEquals(ReminderStatus.DISMISSED, repository.statusOf("r1"))
    }

    @Test
    fun trasDispararseElRecordatorio_elEstadoVuelveAOfrecerLosAtajos() = runTest {
        val repository = FakeReminderRepository(now, taskTitles = mapOf("t1" to "Informe"))
        repository.put(newReminder("t1", now.plusSeconds(60), now, clientId = "r1"))
        val viewModel = viewModel(repository).apply { open("t1") }

        repository.claimDue(now.plusSeconds(61))

        assertEquals(ReminderStatus.SENT, repository.statusOf("r1"))
        assertNull(viewModel.uiState.value.reminder)
    }

    @Test
    fun trasDescartarseElRecordatorioPorTareaCompletada_elEstadoVuelveAOfrecerLosAtajos() = runTest {
        val repository = FakeReminderRepository(now, completedTasks = setOf("t1"))
        repository.put(newReminder("t1", now.plusSeconds(60), now, clientId = "r1"))
        val viewModel = viewModel(repository).apply { open("t1") }

        repository.claimDue(now.plusSeconds(61))

        assertEquals(ReminderStatus.DISMISSED, repository.statusOf("r1"))
        assertNull(viewModel.uiState.value.reminder)
    }

    @Test
    fun enModoCreacion_sinTarea_noCreaNadaYElEstadoEstaVacio() = runTest {
        val repository = FakeReminderRepository(now)
        val viewModel = viewModel(repository)

        viewModel.laterToday()

        assertTrue(repository.created.isEmpty())
        assertEquals(RemindersUiState(), viewModel.uiState.value)
    }

    @Test
    fun close_reiniciaElEstado() = runTest {
        val viewModel = viewModel(FakeReminderRepository(now)).apply { open("t1") }

        viewModel.close()

        assertNull(viewModel.uiState.value.taskId)
    }
}
