package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.sync.BackgroundSyncRules
import com.simposio.taskeer.testing.signedIn
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingBackgroundScheduler : BackgroundSyncScheduler {
    val pushes = mutableListOf<Unit>()
    val retries = mutableListOf<Duration>()
    val pulls = mutableListOf<Duration>()
    var cancels = 0

    override fun enqueuePush() {
        pushes += Unit
    }

    override fun enqueueRetry(delay: Duration) {
        retries += delay
    }

    override fun enqueuePeriodicPull(interval: Duration) {
        pulls += interval
    }

    override fun cancelAll() {
        cancels++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundSyncControllerTest {

    private val session = MutableStateFlow<SessionState>(signedIn())
    private val enabled = MutableStateFlow(true)
    private val pending = MutableStateFlow(0)
    private val scheduler = RecordingBackgroundScheduler()
    private val controller = BackgroundSyncController(session, enabled, pending, scheduler)

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.run() }
    }

    @Test
    fun pendientesDeCeroATresYCinco_unSoloEnvio() = runTest {
        start()

        pending.value = 3
        pending.value = 5

        assertEquals(1, scheduler.pushes.size)
    }

    @Test
    fun sinPendientes_noEncolaEnvio() = runTest {
        start()

        assertEquals(0, scheduler.pushes.size)
    }

    @Test
    fun conSesionYAjusteActivo_encolaLaTraidaPeriodicaUnaVez() = runTest {
        start()

        session.value = signedIn(offline = true)
        session.value = signedIn()

        assertEquals(listOf(BackgroundSyncRules.PULL_INTERVAL), scheduler.pulls)
    }

    @Test
    fun sesionCerrada_cancelaTodoUnaVez() = runTest {
        start()

        session.value = SessionState.SignedOut(expired = false)

        assertEquals(1, scheduler.cancels)
        assertFalse(controller.active.value)
    }

    @Test
    fun resuming_noCancelaNiEncola() = runTest {
        start()

        session.value = SessionState.Resuming

        assertEquals(0, scheduler.cancels)
        assertEquals(1, scheduler.pulls.size)
    }

    @Test
    fun cerrarSesionYVolverAEntrar_reencolaLaTraida() = runTest {
        start()
        session.value = SessionState.SignedOut(expired = true)

        session.value = SessionState.Resuming
        session.value = signedIn()

        assertEquals(2, scheduler.pulls.size)
    }

    @Test
    fun desactivarElAjuste_cancelaYElReintentoNoEncola() = runTest {
        val retry = BackgroundRetryScheduler(scheduler) { controller.active.value }
        start()

        enabled.value = false
        retry.schedule(Duration.ofSeconds(8))

        assertEquals(1, scheduler.cancels)
        assertEquals(emptyList<Duration>(), scheduler.retries)
    }

    @Test
    fun conElAjusteApagado_losPendientesNoEncolan() = runTest {
        start()
        enabled.value = false

        pending.value = 2

        assertEquals(0, scheduler.pushes.size)
    }

    @Test
    fun reactivarElAjuste_reencolaTraidaYEnvioPendiente() = runTest {
        start()
        enabled.value = false
        pending.value = 2

        enabled.value = true

        assertEquals(2, scheduler.pulls.size)
        assertEquals(1, scheduler.pushes.size)
    }

    @Test
    fun conElAjusteActivo_elReintentoSeEncolaConElRetrasoDelMotor() = runTest {
        val retry = BackgroundRetryScheduler(scheduler) { controller.active.value }
        start()

        retry.schedule(Duration.ofSeconds(8))

        assertEquals(listOf(Duration.ofSeconds(8)), scheduler.retries)
        assertTrue(controller.active.value)
    }

    @Test
    fun compuesto_repartePorIgualElRetraso() {
        val received = mutableListOf<Duration>()
        val composite = CompositeRetryScheduler(
            RetryScheduler { received += it },
            BackgroundRetryScheduler(scheduler) { true },
        )

        composite.schedule(Duration.ofSeconds(8))

        assertEquals(listOf(Duration.ofSeconds(8)), received)
        assertEquals(listOf(Duration.ofSeconds(8)), scheduler.retries)
    }
}
