package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.sync.SyncEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import com.simposio.taskeer.testing.FakeSession
import com.simposio.taskeer.testing.DirectTransportSource
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRejectLoggingTest {

    private val dao = FakeSyncDao()
    private val lines = mutableListOf<String>()
    private val scope = SyncScopeHolder(setOf(SyncEntity.LIST, SyncEntity.TASK))
    private val store = RoomSyncLocalStore(dao, scope, newMutationId = { "m" }, debugLog = { lines += it })
    private val now = Instant.ofEpochMilli(100_000L)

    private suspend fun rejectTask(code: String, record: ServerRecord?) {
        dao.put(syncList("list-1", pending = false))
        dao.put(syncTask("0123456789abcdef"))
        store.collect(now)
        val op = store.eligible(now).single()
        val prepared = checkNotNull(store.payloadFor(op, "me"))
        store.applyOutcome(prepared, PushOutcome.Rejected(op.mutationId, code, record), "me", now, retryDependency = false)
    }

    @Test
    fun rechazoValidacion_registraIdCompletoCodigoYAccion() = runTest {
        rejectTask("VALIDATION_ERROR", null)

        assertEquals(listOf("reject entity=task op=upsert id=0123456789abcdef code=VALIDATION_ERROR record=false action=KEPT_FOR_RETRY"), lines)
    }

    @Test
    fun rechazoSinRegistro_registraDescartado() = runTest {
        rejectTask("FORBIDDEN", null)

        assertTrue(lines.single().endsWith("code=FORBIDDEN record=false action=DISCARDED"))
    }

    @Test
    fun rechazoConRegistro_registraAdopcion() = runTest {
        val record = ServerRecord(SyncEntity.TASK, "0123456789abcdef", Instant.ofEpochMilli(90_000L), null, mapOf("title" to "Servidor"))

        rejectTask("FORBIDDEN", record)

        assertTrue(lines.single().endsWith("code=FORBIDDEN record=true action=ADOPTED_SERVER_RECORD"))
    }

    @Test
    fun ciclo_registraInicioFinResumenYContenidoDeLoFallido() = runTest {
        val log = mutableListOf<String>()
        val rig = SyncRig()
        rig.bound()
        rig.dao.put(syncTask("t1", listId = "no-existe", title = "Titulo enviado"))
        val engine = with(rig) {
            SyncEngine(
                store = RoomSyncLocalStore(dao, scopeHolder, newMutationId = { "m1" }, debugLog = { log += it }),
                source = DirectTransportSource(backend),
                gate = SyncSessionGate(store),
                wiper = LocalDataWiper(dao) { now },
                session = FakeSession().state,
                scopeHolder = scopeHolder,
                triggers = SyncTriggers(
                    emptyFlow(), emptyFlow(),
                    CoroutineScope(StandardTestDispatcher(testScheduler)),
                ),
                retry = { },
                clock = { now },
                debugLog = { log += it },
            )
        }

        engine.runCycle(manual = true)

        assertTrue(log.toString(), log.first() == "cycle start manual=true")
        assertTrue(log.toString(), log.last().startsWith("cycle end outcome="))
        assertTrue(log.toString(), log.any { it.startsWith("push summary applied=") })
        assertTrue(log.toString(), log.any { it.startsWith("pull page=1 ") })
        assertTrue(log.toString(), log.any { it.startsWith("push mutation entity=task op=upsert id=t1 mid=") })
    }
}
