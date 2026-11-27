package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.testing.DirectTransportSource
import com.simposio.taskeer.testing.FakeSession
import com.simposio.taskeer.testing.FakeSyncBackend
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.signedIn
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncTriggersTest {

    private val dao = FakeSyncDao()
    private val backend = FakeSyncBackend()
    private val scopeHolder = SyncScopeHolder()
    private val store = RoomSyncLocalStore(dao, scopeHolder)
    private val connectivity = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    private val pending = MutableStateFlow(0)
    private val cycleStarts = mutableListOf<Long>()

    private inner class TimestampingSource(private val inner: TransportSource) : TransportSource {
        var currentTime: () -> Long = { 0L }

        override suspend fun acquire(): Acquisition {
            cycleStarts += currentTime()
            return inner.acquire()
        }

        override fun invalidate() = inner.invalidate()
    }

    private class Rig(val engine: SyncEngine, val triggers: SyncTriggers, val session: FakeSession)

    private fun TestScope.rig(session: FakeSession = FakeSession()): Rig {
        val triggers = SyncTriggers(connectivity, pending, backgroundScope)
        val source = TimestampingSource(DirectTransportSource(backend)).also { it.currentTime = { testScheduler.currentTime } }
        val engine = SyncEngine(
            store = store,
            source = source,
            gate = SyncSessionGate(store),
            wiper = LocalDataWiper(dao),
            session = session.state,
            scopeHolder = scopeHolder,
            triggers = triggers,
            retry = triggers,
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        backgroundScope.launch { engine.run() }
        return Rig(engine, triggers, session)
    }

    @Test
    fun rafagaDeDisparadores_unCiclo() = runTest {
        val rig = rig()
        runCurrent()

        repeat(5) {
            rig.triggers.foreground()
            connectivity.emit(true)
        }
        advanceTimeBy(5_000)

        assertEquals(1, cycleStarts.size)
    }

    @Test
    fun manual_ignoraElRetroceso() = runTest {
        dao.put(syncList("list-1", pending = false))
        dao.baseline("list", "list-1", 1L)
        dao.put(syncTask("t1"))
        backend.seed(com.simposio.taskeer.domain.sync.SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        backend.transportFailures += TransportResult.Retry(java.time.Duration.ofMinutes(10), RetryKind.RATE_LIMITED)
        val rig = rig()
        rig.engine.runCycle(manual = false)
        val afterFailure = backend.pushCalls

        rig.engine.runCycle(manual = false)

        assertEquals(afterFailure, backend.pushCalls)

        rig.engine.runCycle(manual = true)

        assertEquals(afterFailure + 1, backend.pushCalls)
        assertTrue(backend.task("t1") != null)
    }

    @Test
    fun afterWrite_antirrebote() = runTest {
        val triggers = SyncTriggers(connectivity, pending, backgroundScope)
        val arrivals = mutableListOf<Long>()
        backgroundScope.launch { triggers.requests().collect { arrivals += testScheduler.currentTime } }
        runCurrent()

        pending.value = 1
        advanceTimeBy(500)
        pending.value = 2
        advanceTimeBy(500)
        pending.value = 3
        advanceTimeBy(10_000)

        assertEquals(1, arrivals.size)
        assertTrue("el ciclo sale tras 2 s de silencio: ${arrivals.single()}", arrivals.single() >= 3_000)
    }

    @Test
    fun ciclosAutomaticos_espaciados10s() = runTest {
        val rig = rig()
        runCurrent()

        rig.triggers.foreground()
        advanceTimeBy(1_000)
        rig.triggers.foreground()
        advanceTimeBy(30_000)

        assertEquals(2, cycleStarts.size)
        assertTrue("separación ${cycleStarts[1] - cycleStarts[0]} ms", cycleStarts[1] - cycleStarts[0] >= 10_000)
    }

    @Test
    fun sinSesion_noLlamaAlTransporte() = runTest {
        val rig = rig(FakeSession(SessionState.SignedOut()))
        dao.put(syncList("list-1", pending = false))
        dao.put(syncTask("t1"))
        runCurrent()

        rig.triggers.manual()
        rig.triggers.foreground()
        connectivity.emit(true)
        advanceTimeBy(30_000)

        assertEquals(0, backend.identifyCalls)
        assertEquals(0, backend.pushCalls)
        assertEquals(1, dao.queue().size)
    }

    @Test
    fun signedInOffline_espera() = runTest {
        val rig = rig(FakeSession(signedIn(offline = true)))
        dao.put(syncList("list-1", pending = false))
        dao.baseline("list", "list-1", 1L)
        backend.seed(com.simposio.taskeer.domain.sync.SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        runCurrent()

        rig.triggers.manual()
        advanceTimeBy(30_000)

        assertEquals(0, backend.pushCalls)

        rig.session.state.value = signedIn(offline = false)
        advanceTimeBy(30_000)

        assertEquals(1, backend.pushCalls)
        assertTrue(backend.task("t1") != null)
    }

    @Test
    fun retroceso_programaElReintentoAutomatico() = runTest {
        dao.put(syncList("list-1", pending = false))
        dao.baseline("list", "list-1", 1L)
        backend.seed(com.simposio.taskeer.domain.sync.SyncEntity.LIST, "list-1", mapOf("name" to "Tareas"), Instant.EPOCH)
        dao.put(syncTask("t1"))
        backend.transportFailures += TransportResult.Retry(null, RetryKind.NETWORK)
        val rig = rig()
        runCurrent()

        rig.triggers.manual()
        advanceTimeBy(120_000)

        assertTrue("el reintento programado debe haber enviado: ${backend.pushCalls}", backend.task("t1") != null)
    }
}
