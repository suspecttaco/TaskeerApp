package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncErrorKind
import com.simposio.taskeer.testing.SyncRig
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Cómo reacciona el motor a cada resultado de transporte durante la traída.
class SyncEnginePullErrorsTest {

    private val rig = SyncRig()
    private val backend get() = rig.backend

    @Test
    fun pull426_pausaPorActualizacion_sinMarcarSincronizado() = runTest {
        rig.bound()
        backend.pullFailures += TransportResult.UpdateRequired
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)

        val snapshot = engine.snapshots.first()
        assertEquals(PauseReason.UPDATE_REQUIRED, snapshot.paused)
        assertNull(snapshot.lastSyncedAt)
    }

    @Test
    fun pull429_reprogramaRespetandoRetryAfter() = runTest {
        rig.bound()
        backend.pullFailures += TransportResult.Retry(Duration.ofSeconds(30), RetryKind.RATE_LIMITED)
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)

        assertTrue(rig.scheduled.any { it >= Duration.ofSeconds(30) })
        assertNull(engine.snapshots.first().lastSyncedAt)
        assertNull(engine.snapshots.first().paused)
    }

    @Test
    fun pull5xxSeguidos_elRetrocesoCrece() = runTest {
        rig.bound()
        backend.pullFailures += TransportResult.Retry(null, RetryKind.SERVER)
        backend.pullFailures += TransportResult.Retry(null, RetryKind.SERVER)
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)
        engine.runCycle(manual = true)

        // 2 s y 4 s, cada uno con ±20 % de variación.
        assertEquals(2, rig.scheduled.size)
        assertTrue(rig.scheduled[0] >= Duration.ofMillis(1_600) && rig.scheduled[0] <= Duration.ofMillis(2_400))
        assertTrue(rig.scheduled[1] >= Duration.ofMillis(3_200) && rig.scheduled[1] <= Duration.ofMillis(4_800))
        assertNull(engine.snapshots.first().lastSyncedAt)
    }

    @Test
    fun pull401_detieneSinPausarNiProgramar() = runTest {
        rig.bound()
        backend.pullFailures += TransportResult.Unauthorized
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)

        assertEquals(1, backend.pullCalls)
        assertNull(engine.snapshots.first().paused)
        assertNull(engine.snapshots.first().lastSyncedAt)
        assertTrue(rig.scheduled.isEmpty())
    }

    @Test
    fun pull404_servidorNoPreparado() = runTest {
        rig.bound()
        backend.pullFailures += TransportResult.NotAvailable
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)

        assertEquals(PauseReason.SERVER_NOT_READY, engine.snapshots.first().paused)
        assertNull(engine.snapshots.first().lastSyncedAt)
    }

    // Sin el guard de un solo reinicio, un servidor que siempre responde INVALID_CURSOR haría reintentar sin fin.
    @Test
    fun cursorInvalidoDosVeces_noHaceBucle() = runTest {
        rig.bound()
        val stale = "i:5:0:viejo"
        rig.dao.metas.value = rig.dao.metas.value + (SyncMetaKeys.CURSOR to stale)
        backend.invalidCursors += stale
        repeat(5) { backend.pullFailures += TransportResult.Invalid(SyncCodes.INVALID_CURSOR) }
        val engine = with(rig) { engine() }

        engine.runCycle(manual = true)

        assertEquals(listOf(stale, null), backend.pulls.map { it.first })
        assertEquals(SyncErrorKind.UNEXPECTED_RESPONSE, engine.snapshots.first().cycleError)
        assertNull(rig.dao.metas.value[SyncMetaKeys.CURSOR])
        assertNull(engine.snapshots.first().lastSyncedAt)
    }
}
