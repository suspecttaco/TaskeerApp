package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.sync.PauseReason
import com.simposio.taskeer.domain.sync.SeededListId
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.DirectTransportSource
import com.simposio.taskeer.testing.FakeSession
import com.simposio.taskeer.testing.FakeSyncBackend
import com.simposio.taskeer.testing.FakeSyncDao
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.USER_B
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountBindingTest {

    private val dao = FakeSyncDao()
    private val backend = FakeSyncBackend()
    private val scopeHolder = SyncScopeHolder()
    private val store = RoomSyncLocalStore(dao, scopeHolder)
    private val now = Instant.parse("2026-11-26T18:30:00Z")
    private val session = FakeSession()

    private fun TestScope.engine(): SyncEngine = SyncEngine(
        store = store,
        source = DirectTransportSource(backend),
        gate = SyncSessionGate(store),
        wiper = LocalDataWiper(dao) { now },
        session = session.state,
        scopeHolder = scopeHolder,
        triggers = SyncTriggers(emptyFlow(), emptyFlow(), CoroutineScope(StandardTestDispatcher(testScheduler))),
        retry = { },
        clock = { now },
    )

    private fun seedLocal() {
        dao.put(syncList(SEEDED_TASK_LIST_ID, pending = false))
        dao.put(syncTask("t1", listId = SEEDED_TASK_LIST_ID, pending = false))
    }

    @Test
    fun primerLogin_vinculaYSubeLoLocal() = runTest {
        seedLocal()

        engine().runCycle(manual = false)

        assertEquals(USER_A, dao.metas.value[SyncMetaKeys.BOUND_USER_ID])
        assertNotNull(backend.task("t1"))
        assertNotNull(backend.records[SyncEntity.LIST to SeededListId.forUser(USER_A)])
    }

    @Test
    fun vinculacion_derivaIdDeTareasPorUsuario() = runTest {
        seedLocal()

        engine().runCycle(manual = false)

        val derived = SeededListId.forUser(USER_A)
        assertNull(dao.lists.value[SEEDED_TASK_LIST_ID])
        assertNotNull(dao.lists.value[derived])
        assertEquals(derived, dao.tasks.value.getValue("t1").listId)
    }

    @Test
    fun vinculacion_noAplicaLaPaginaDeIdentify() = runTest {
        seedLocal()
        backend.seed(SyncEntity.TASK, "remota", mapOf("title" to "ajena", "listId" to SeededListId.forUser(USER_A)), now)
        backend.failPullCalls += 1

        engine().runCycle(manual = false)

        assertEquals(1, backend.identifyCalls)
        assertNull(dao.tasks.value["remota"])
    }

    @Test
    fun otroUsuario_pausaSinPeticiones() = runTest {
        seedLocal()
        dao.metas.value = mapOf(SyncMetaKeys.BOUND_USER_ID to USER_A)
        backend.userId = USER_B
        val engine = engine()

        engine.runCycle(manual = false)

        assertEquals(0, backend.pushCalls)
        assertEquals(PauseReason.ACCOUNT_MISMATCH, engine.snapshots.first().paused)
        assertTrue(engine.accountSwitchPrompt.value)
    }

    @Test
    fun otroUsuario_pausaAntesDeAplicarNada() = runTest {
        seedLocal()
        dao.metas.value = mapOf(SyncMetaKeys.BOUND_USER_ID to USER_A)
        backend.userId = USER_B
        backend.seed(SyncEntity.TASK, "remota", mapOf("title" to "ajena"), now)

        engine().runCycle(manual = false)

        assertEquals(setOf("t1"), dao.tasks.value.keys)
        assertEquals(USER_A, dao.metas.value[SyncMetaKeys.BOUND_USER_ID])
    }

    @Test
    fun cancelar_noBorraNada() = runTest {
        seedLocal()
        dao.metas.value = mapOf(SyncMetaKeys.BOUND_USER_ID to USER_A)
        backend.userId = USER_B
        val engine = engine()
        engine.runCycle(manual = false)

        engine.cancelAccountSwitch()

        assertEquals(setOf("t1"), dao.tasks.value.keys)
        assertEquals(PauseReason.ACCOUNT_MISMATCH, engine.snapshots.first().paused)
    }

    @Test
    fun confirmarVaciado_dejaBaseLimpiaYResiembra() = runTest {
        seedLocal()
        dao.metas.value = mapOf(SyncMetaKeys.BOUND_USER_ID to USER_A)
        backend.userId = USER_B
        val engine = engine()
        engine.runCycle(manual = false)

        engine.confirmAccountWipe()

        assertTrue(dao.tasks.value.isEmpty())
        assertEquals(setOf(SEEDED_TASK_LIST_ID), dao.lists.value.keys)
        assertNull(dao.metas.value[SyncMetaKeys.BOUND_USER_ID])
        assertNull(engine.snapshots.first().paused)
    }

    @Test
    fun logout_conservaColaCursorYVinculo() = runTest {
        seedLocal()
        val engine = engine()
        engine.runCycle(manual = false)
        dao.put(syncTask("t2", listId = SeededListId.forUser(USER_A)))
        dao.metas.value = dao.metas.value + (SyncMetaKeys.LAST_SYNCED_AT to "123")

        session.state.value = SessionState.SignedOut()

        assertEquals(USER_A, dao.metas.value[SyncMetaKeys.BOUND_USER_ID])
        assertEquals("123", dao.metas.value[SyncMetaKeys.LAST_SYNCED_AT])
        assertTrue(dao.tasks.value.getValue("t2").pending)
    }

    @Test
    fun mismaCuentaTrasLogout_reanuda() = runTest {
        seedLocal()
        val engine = engine()
        engine.runCycle(manual = false)
        session.state.value = SessionState.SignedOut()
        dao.put(syncTask("t2", listId = SeededListId.forUser(USER_A)))
        session.state.value = com.simposio.taskeer.testing.signedIn()

        engine.runCycle(manual = true)

        assertNotNull(backend.task("t2"))
        assertNull(engine.snapshots.first().paused)
    }

    @Test
    fun rekeyActualizaTareasColaYBaseline() = runTest {
        val newId = SeededListId.forUser(USER_A)
        dao.put(syncList(SEEDED_TASK_LIST_ID))
        dao.put(syncTask("t1", listId = SEEDED_TASK_LIST_ID))
        dao.baseline("list", SEEDED_TASK_LIST_ID, 5L)

        store.rekeyList(SEEDED_TASK_LIST_ID, newId)

        assertNull(dao.lists.value[SEEDED_TASK_LIST_ID])
        assertEquals(newId, dao.tasks.value.getValue("t1").listId)
        assertEquals(5L, dao.baseline("list", newId)?.serverUpdatedAt)
        assertNull(dao.baseline("list", SEEDED_TASK_LIST_ID))
    }

    @Test
    fun vaciaTodasLasTablas() = runTest {
        seedLocal()
        dao.baseline("task", "t1", 1L)
        dao.metas.value = mapOf(SyncMetaKeys.BOUND_USER_ID to USER_A)

        LocalDataWiper(dao) { now }.wipe()

        assertEquals(
            setOf("boards", "columns", "tasks", "subtasks", "my_day_tasks", "reminders", "app_notifications", "task_lists",
                "board_access", "sync_workspaces",
            ),
            dao.wipedBusinessTables.toSet(),
        )
        assertTrue(dao.baselines.value.isEmpty())
        assertTrue(dao.queueRows.value.isEmpty())
        assertTrue(dao.metas.value.isEmpty())
    }

    @Test
    fun resiembraListaTareas() = runTest {
        seedLocal()

        LocalDataWiper(dao) { now }.wipe()

        val seeded = dao.lists.value.getValue(SEEDED_TASK_LIST_ID)
        assertEquals("Tareas", seeded.name)
        assertTrue(seeded.pending)
    }
}
