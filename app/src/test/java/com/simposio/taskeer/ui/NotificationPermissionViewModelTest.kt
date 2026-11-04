package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.reminder.newReminder
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.testing.FakeReminderRepository
import com.simposio.taskeer.ui.permission.NotificationPermissionViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private var granted = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        reminders: FakeReminderRepository = FakeReminderRepository(now),
        sdkInt: Int = 34,
    ) = NotificationPermissionViewModel(preferences, reminders, { granted }, sdkInt)

    @Test
    fun somethingToNotify_conElAvisoPorFechaLimiteActivo_esVerdadero() = runTest {
        val preferences = FakePreferencesRepository(deadline = DeadlineReminderSettings(enabled = true))

        assertTrue(viewModel(preferences).somethingToNotify.value)
    }

    @Test
    fun somethingToNotify_conElResumenActivo_esVerdadero() = runTest {
        val preferences = FakePreferencesRepository(digest = DigestSettings(enabled = true))

        assertTrue(viewModel(preferences).somethingToNotify.value)
    }

    @Test
    fun somethingToNotify_conUnRecordatorioManualPendiente_esVerdadero() = runTest {
        val reminders = FakeReminderRepository(now)
        reminders.put(newReminder("t1", now.plusSeconds(60), now))

        assertTrue(viewModel(reminders = reminders).somethingToNotify.value)
    }

    @Test
    fun somethingToNotify_sinNada_esFalso() = runTest {
        assertFalse(viewModel().somethingToNotify.value)
    }

    @Test
    fun takeRequest_hayAlgoQueAvisarSinPermiso_pideUnaSolaVezPorTransicion() = runTest {
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(preferences)
        preferences.setDigestEnabled(true)

        assertTrue(viewModel.takeRequest())
        assertFalse(viewModel.takeRequest())
    }

    @Test
    fun takeRequest_trasUnaTransicionFalsoVerdaderoVuelveAPedir() = runTest {
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(preferences)
        preferences.setDigestEnabled(true)
        viewModel.takeRequest()

        preferences.setDigestEnabled(false)
        preferences.setDigestEnabled(true)

        assertTrue(viewModel.takeRequest())
    }

    @Test
    fun blocked_trasDenegarElPermiso_esVerdaderoYSeLimpiaAlConceder() = runTest {
        val preferences = FakePreferencesRepository(digest = DigestSettings(enabled = true))
        val viewModel = viewModel(preferences)
        assertFalse(viewModel.blocked.value)

        viewModel.takeRequest()
        viewModel.onPermissionResult(false)
        assertTrue(viewModel.blocked.value)

        granted = true
        viewModel.refreshGranted()
        assertFalse(viewModel.blocked.value)
    }

    @Test
    fun blocked_enAndroidAnterior33_nuncaEsVerdadero() = runTest {
        val preferences = FakePreferencesRepository(digest = DigestSettings(enabled = true))
        val viewModel = viewModel(preferences, sdkInt = 32)

        viewModel.onPermissionResult(false)

        assertFalse(viewModel.blocked.value)
    }

    @Test
    fun blocked_alQuedarseSinNadaQueAvisar_desapareceElAviso() = runTest {
        val preferences = FakePreferencesRepository(digest = DigestSettings(enabled = true))
        val viewModel = viewModel(preferences)
        viewModel.takeRequest()
        viewModel.onPermissionResult(false)

        preferences.setDigestEnabled(false)

        assertFalse(viewModel.blocked.value)
    }
}
