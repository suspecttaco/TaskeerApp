package com.simposio.taskeer.ui.sync

import com.simposio.taskeer.data.repository.SyncRepository
import com.simposio.taskeer.domain.sync.CycleOutcome
import com.simposio.taskeer.domain.sync.ManualSyncFeedback
import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncErrorKind
import com.simposio.taskeer.domain.sync.SyncLoss
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncNoticeRules
import com.simposio.taskeer.domain.sync.SyncSnapshot
import com.simposio.taskeer.domain.sync.SyncStatus
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private class FakeSyncRepository : SyncRepository {
        val snapshot = MutableStateFlow(SyncSnapshot(signedIn = true))
        val prompt = MutableStateFlow(false)
        val outcomes = MutableSharedFlow<CycleOutcome>(extraBufferCapacity = 4)
        val noticeFlow = MutableSharedFlow<SyncNotice>(extraBufferCapacity = 4)
        var requests = 0
        var wipes = 0
        var cancels = 0
        override val status = snapshot
        override val notices = noticeFlow
        override val manualOutcomes = outcomes
        override val accountSwitchPrompt: StateFlow<Boolean> = prompt

        override fun requestSync() { requests++ }
        override fun notifyForeground() = Unit
        override suspend fun confirmAccountWipe() { wipes++ }
        override fun cancelAccountSwitch() { cancels++ }
    }

    private val repo = FakeSyncRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SyncViewModel(repo).also {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { it.state.collect { } }
    }

    @Test
    fun estadoIdle_mostrarSincronizado() = runTest {
        val last = Instant.parse("2026-11-26T18:00:00Z")
        repo.snapshot.value = SyncSnapshot(signedIn = true, lastSyncedAt = last)

        val state = viewModel().state.value

        assertEquals(SyncStatus.Idle(last), state.status)
        assertTrue(state.visible)
    }

    @Test
    fun pendientes_muestraConteo() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, pendingCount = 3)

        assertEquals(SyncStatus.Pending(3), viewModel().state.value.status)
    }

    @Test
    fun error_muestraError() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, stuckCount = 1)

        assertEquals(SyncStatus.Error(SyncErrorKind.STUCK_ENTRIES), viewModel().state.value.status)
    }

    @Test
    fun pausadaPorCuenta_abreDialogo() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, paused = PauseReason.ACCOUNT_MISMATCH)
        repo.prompt.value = true

        val vm = viewModel()

        assertTrue(vm.state.value.accountDialogOpen)
        vm.confirmAccountWipe()
        vm.cancelAccountSwitch()
        assertEquals(1, repo.wipes)
        assertEquals(1, repo.cancels)
    }

    @Test
    fun updateRequired_avisaActualizar() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, paused = PauseReason.UPDATE_REQUIRED)

        assertEquals(SyncStatus.Paused(PauseReason.UPDATE_REQUIRED), viewModel().state.value.status)
    }

    @Test
    fun serverNotReady_muestraPausada() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, paused = PauseReason.SERVER_NOT_READY)

        val state = viewModel().state.value

        assertEquals(SyncStatus.Paused(PauseReason.SERVER_NOT_READY), state.status)
        assertTrue(state.visible)
    }

    @Test
    fun sinSesion_nadaVisible() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = false, pendingCount = 4)

        assertFalse(viewModel().state.value.visible)
    }

    @Test
    fun manual_llamaRequestSync() = runTest {
        viewModel().sync()

        assertEquals(1, repo.requests)
    }

    private fun collectFeedback(vm: SyncViewModel): MutableList<ManualSyncFeedback> {
        val feedback = mutableListOf<ManualSyncFeedback>()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.manualFeedback.collect { feedback += it } }
        return feedback
    }

    @Test
    fun manual_avisaSincronizandoYLuegoTodoSincronizado() = runTest {
        val vm = viewModel()
        val feedback = collectFeedback(vm)

        vm.sync()
        repo.outcomes.emit(CycleOutcome.COMPLETED)

        assertEquals(
            listOf(
                ManualSyncFeedback.Status(SyncStatus.Syncing),
                ManualSyncFeedback.Status(SyncStatus.Idle(null)),
            ),
            feedback,
        )
    }

    @Test
    fun manual_pausadoAvisaConLaRazon() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, paused = PauseReason.UPDATE_REQUIRED)
        val vm = viewModel()
        val feedback = collectFeedback(vm)

        vm.sync()
        repo.outcomes.emit(CycleOutcome.PAUSED)

        assertEquals(
            ManualSyncFeedback.Status(SyncStatus.Paused(PauseReason.UPDATE_REQUIRED)),
            feedback.last(),
        )
    }

    @Test
    fun manual_respuestaInesperadaAvisaError() = runTest {
        val vm = viewModel()
        val feedback = collectFeedback(vm)

        vm.sync()
        repo.outcomes.emit(CycleOutcome.STOPPED_BY_ERROR)

        assertEquals(
            ManualSyncFeedback.Status(SyncStatus.Error(SyncErrorKind.UNEXPECTED_RESPONSE)),
            feedback.last(),
        )
    }

    @Test
    fun manual_conCicloEnCursoNoPideOtro() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = true, syncing = true)

        viewModel().sync()

        assertEquals(0, repo.requests)
    }

    @Test
    fun manual_sinSesionNoPideCiclo() = runTest {
        repo.snapshot.value = SyncSnapshot(signedIn = false)

        viewModel().sync()

        assertEquals(0, repo.requests)
    }

    @Test
    fun manual_dosToquesSeguidosPidenUnSoloCiclo() = runTest {
        val vm = viewModel()

        vm.sync()
        vm.sync()

        assertEquals(1, repo.requests)
    }

    @Test
    fun avisoAgrupado() = runTest {
        val vm = viewModel()
        val messages = mutableListOf<String>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.noticeMessages.collect { messages += it } }

        repo.noticeFlow.emit(SyncNoticeRules.summarize(listOf(SyncLoss.REJECTED, SyncLoss.REJECTED, SyncLoss.REPLACED_BY_SERVER))!!)
        job.cancel()

        assertEquals(1, messages.size)
        assertTrue(messages.single().startsWith("Sincronización: 2 cambios no se aceptaron"))
    }
}
