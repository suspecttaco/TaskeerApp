package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SyncEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTransportSelectorTest {

    private class Stub(var identity: TransportResult<Identity>) : SyncTransport {
        var identifyCalls = 0

        override suspend fun push(batch: List<PushMutation>): TransportResult<List<PushOutcome>> = TransportResult.Ok(emptyList())

        override suspend fun identify(): TransportResult<Identity> {
            identifyCalls++
            return identity
        }
    }

    private fun ok(version: String = "1.0", vararg entities: SyncEntity) =
        TransportResult.Ok(Identity("u", version, entities.toSet()))

    private val all = arrayOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK)

    @Test
    fun h1Desplegado_usaB7NoElPuente() = runTest {
        val contract = Stub(ok("1.0", *all))
        val bridge = Stub(ok("bridge", *all))

        val ready = SyncTransportSelector(contract, bridge).acquire() as Acquisition.Ready

        assertSame(contract, ready.transport)
        assertEquals(0, bridge.identifyCalls)
    }

    @Test
    fun rutaInexistente_debugUsaPuente() = runTest {
        val bridge = Stub(ok("bridge", *all))

        val ready = SyncTransportSelector(Stub(TransportResult.NotAvailable), bridge).acquire() as Acquisition.Ready

        assertSame(bridge, ready.transport)
    }

    @Test
    fun rutaInexistente_releaseEsServerNotReady() = runTest {
        val result = SyncTransportSelector(Stub(TransportResult.NotAvailable), bridge = null).acquire() as Acquisition.Paused

        assertEquals(PauseReason.SERVER_NOT_READY, result.reason)
    }

    @Test
    fun supportedEntitiesVacio_comoInexistente() = runTest {
        val result = SyncTransportSelector(Stub(ok("1.0")), bridge = null).acquire() as Acquisition.Paused

        assertEquals(PauseReason.SERVER_NOT_READY, result.reason)
    }

    @Test
    fun majorDistinta_updateRequired() = runTest {
        val result = SyncTransportSelector(Stub(TransportResult.UpdateRequired), Stub(ok("bridge", *all))).acquire() as Acquisition.Paused

        assertEquals(PauseReason.UPDATE_REQUIRED, result.reason)
    }

    @Test
    fun forzadoPorPropiedad_omiteSondeo() = runTest {
        val contract = Stub(ok("1.0", *all))
        val bridge = Stub(ok("bridge", *all))

        val ready = SyncTransportSelector(contract, bridge, TransportMode.BRIDGE).acquire() as Acquisition.Ready

        assertSame(bridge, ready.transport)
        assertEquals(0, contract.identifyCalls)
    }

    @Test
    fun unTransportePorCiclo() = runTest {
        val contract = Stub(ok("1.0", *all))
        val selector = SyncTransportSelector(contract, bridge = null)

        val first = selector.acquire() as Acquisition.Ready
        val second = selector.acquire() as Acquisition.Ready

        assertSame(first.transport, second.transport)
        assertEquals(1, contract.identifyCalls)
        selector.invalidate()
        selector.acquire()
        assertTrue(contract.identifyCalls == 2)
    }
}
