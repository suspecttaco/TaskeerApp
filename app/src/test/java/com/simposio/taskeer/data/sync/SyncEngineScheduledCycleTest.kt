package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.CycleOutcome
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineScheduledCycleTest {

    private val rig = SyncRig()

    @Test
    fun cicloCompleto_devuelveCompleted() = runTest {
        rig.bound()

        val outcome = with(rig) { engine().runScheduledCycle() }

        assertEquals(CycleOutcome.COMPLETED, outcome)
    }

    @Test
    fun entradasEnRetroceso_devuelveDeferred() = runTest {
        rig.bound()
        rig.dao.put(syncTask("t1", listId = rig.listId))
        rig.backend.transportFailures += TransportResult.Retry(null, RetryKind.NETWORK)

        val outcome = with(rig) { engine().runScheduledCycle() }

        assertEquals(CycleOutcome.DEFERRED, outcome)
        assertEquals(1, rig.scheduled.size)
    }

    @Test
    fun servidorQueExigeActualizar_devuelveSoloPaused() = runTest {
        rig.bound()
        rig.backend.identifyResult = TransportResult.UpdateRequired

        val outcome = with(rig) { engine().runScheduledCycle() }

        assertEquals(CycleOutcome.PAUSED, outcome)
    }

    @Test
    fun sinSesionValida_devuelveNoSession() = runTest {
        rig.bound()
        rig.backend.identifyResult = TransportResult.Unauthorized

        val outcome = with(rig) { engine().runScheduledCycle() }

        assertEquals(CycleOutcome.NO_SESSION, outcome)
    }

    @Test
    fun cuentaDistinta_devuelvePausedSinEnviarNiRecibir() = runTest {
        rig.bound()
        rig.backend.userId = "otro-usuario"
        rig.dao.put(syncTask("t1", listId = rig.listId))

        val outcome = with(rig) { engine().runScheduledCycle() }

        assertEquals(CycleOutcome.PAUSED, outcome)
        assertEquals(0, rig.backend.pushCalls)
        assertEquals(0, rig.backend.pullCalls)
    }

    @Test
    fun mismasLlamadasYEstadoQueRunCycleEnPrimerPlano() = runTest {
        val foreground = SyncRig()
        foreground.bound()
        foreground.dao.put(syncTask("t1", listId = foreground.listId))
        with(foreground) { engine().runCycle(manual = false) }
        rig.bound()
        rig.dao.put(syncTask("t1", listId = rig.listId))

        with(rig) { engine().runScheduledCycle() }

        assertEquals(foreground.backend.pushCalls, rig.backend.pushCalls)
        assertEquals(foreground.backend.pullCalls, rig.backend.pullCalls)
        assertEquals(foreground.dao.queue().size, rig.dao.queue().size)
        assertEquals(foreground.dao.metas.value, rig.dao.metas.value)
        assertNotNull(rig.backend.task("t1"))
    }

    @Test
    fun cicloLargoEnCurso_elProgramadoEsperaElCandado() = runTest {
        rig.bound()
        rig.dao.put(syncTask("t1", listId = rig.listId))
        val release = CompletableDeferred<Unit>()
        rig.backend.onProcessing = { release.await() }
        val engine = with(rig) { engine() }
        val foreground = async { engine.runCycle(manual = true) }
        advanceUntilIdle()

        val scheduled = async { engine.runScheduledCycle() }
        advanceUntilIdle()

        assertEquals(0, rig.backend.pullCalls)
        release.complete(Unit)
        foreground.await()
        scheduled.await()
        assertEquals(1, rig.backend.maxConcurrent)
        assertEquals(2, rig.backend.pullCalls)
    }
}
