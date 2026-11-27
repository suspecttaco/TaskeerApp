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

        override suspend fun pull(cursor: String?, limit: Int): TransportResult<PullPage> = TransportResult.NotAvailable

        override suspend fun identify(): TransportResult<Identity> {
            identifyCalls++
            return identity
        }
    }

    private fun ok(version: String = "1.0", vararg entities: SyncEntity) =
        TransportResult.Ok(Identity("u", version, entities.toSet()))

    private val all = arrayOf(SyncEntity.LIST, SyncEntity.TASK, SyncEntity.SUBTASK)

    @Test
    fun b7Completo_usaB7() = runTest {
        val contract = Stub(ok("1.0", *all))

        val ready = SyncTransportSelector(contract).acquire() as Acquisition.Ready

        assertSame(contract, ready.transport)
    }

    @Test
    fun sinPuente_rutaInexistente_serverNotReady() = runTest {
        val result = SyncTransportSelector(Stub(TransportResult.NotAvailable)).acquire() as Acquisition.Paused

        assertEquals(PauseReason.SERVER_NOT_READY, result.reason)
    }

    @Test
    fun supportedEntitiesVacio_comoInexistente() = runTest {
        val result = SyncTransportSelector(Stub(ok("1.0"))).acquire() as Acquisition.Paused

        assertEquals(PauseReason.SERVER_NOT_READY, result.reason)
    }

    @Test
    fun majorDistinta_updateRequired() = runTest {
        val result = SyncTransportSelector(Stub(TransportResult.UpdateRequired)).acquire() as Acquisition.Paused

        assertEquals(PauseReason.UPDATE_REQUIRED, result.reason)
    }

    @Test
    fun unTransportePorCiclo() = runTest {
        val contract = Stub(ok("1.0", *all))
        val selector = SyncTransportSelector(contract)

        val first = selector.acquire() as Acquisition.Ready
        val second = selector.acquire() as Acquisition.Ready

        assertSame(first.transport, second.transport)
        assertEquals(1, contract.identifyCalls)
        selector.invalidate()
        selector.acquire()
        assertTrue(contract.identifyCalls == 2)
    }
}
