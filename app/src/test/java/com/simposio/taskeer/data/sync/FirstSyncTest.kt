package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.list.SEEDED_TASK_LIST_ID
import com.simposio.taskeer.domain.sync.SeededListId
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.FakeSyncBackend
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.USER_B
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncList
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Primera vez que una cuenta se vincula: lo local previo (listas y tareas que la migración 13→14 no marcó) sube entero.
class FirstSyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val derived = SeededListId.forUser(USER_A)

    private fun seedLocal() {
        dao.put(syncList(SEEDED_TASK_LIST_ID, pending = false, updatedAt = 7_000L))
        dao.put(syncTask("t1", listId = SEEDED_TASK_LIST_ID, pending = false, updatedAt = 7_100L))
        dao.put(syncTask("t2", listId = SEEDED_TASK_LIST_ID, pending = false, updatedAt = 7_200L))
    }

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    @Test
    fun subeTodoLoLocalSinCambiarUpdatedAt() = runTest {
        seedLocal()

        cycle()

        assertNotNull(backend.task("t1"))
        assertNotNull(backend.task("t2"))
        assertEquals(listOf(7_100L, 7_200L), listOf("t1", "t2").map { dao.tasks.value.getValue(it).updatedAt })
        assertEquals(7_000L, dao.lists.value.getValue(derived).updatedAt)
        assertTrue(dao.tasks.value.values.none { it.pending })
    }

    @Test
    fun listaTareasConIdDerivadoPorUsuario_sinColision() = runTest {
        seedLocal()
        val other = SyncRig()
        other.backend.userId = USER_B
        other.dao.put(syncList(SEEDED_TASK_LIST_ID, pending = false))
        other.dao.put(syncTask("u1", listId = SEEDED_TASK_LIST_ID, pending = false))

        cycle()
        with(other) { engine().runCycle(manual = true) }

        val derivedB = SeededListId.forUser(USER_B)
        assertNotEquals(derived, derivedB)
        assertNotNull(backend.records[SyncEntity.LIST to derived])
        assertNotNull(other.backend.records[SyncEntity.LIST to derivedB])
        assertEquals(derivedB, other.dao.tasks.value.getValue("u1").listId)
    }

    @Test
    fun nombreTomado_adoptaListaDelServidor() = runTest {
        seedLocal()
        backend.seed(SyncEntity.LIST, "lista-del-servidor", mapOf("name" to "Tareas", "position" to 0), at("2026-11-20T10:00:00Z"))

        cycle()

        assertEquals(setOf("lista-del-servidor"), dao.lists.value.values.filter { it.deletedAt == null }.map { it.id }.toSet())
        assertEquals(setOf("lista-del-servidor"), dao.tasks.value.values.map { it.listId }.toSet())
        assertEquals("lista-del-servidor", backend.task("t1")!!.fields["listId"])
        assertTrue(backend.batches.flatten().none { it.entity == SyncEntity.LIST && it.op == SyncOp.DELETE })
    }

    @Test
    fun idYaEnUsoEnListaNoSembrada_rechazoPermanente() = runTest {
        seedLocal()
        dao.put(syncList("extra", name = "Compras", position = 1))
        backend.rejectIds["extra"] = FakeSyncBackend.Rejection("ID_ALREADY_IN_USE")

        cycle()
        cycle()

        assertEquals(1, backend.batches.flatten().count { it.id == "extra" })
        val extra = dao.lists.value.getValue("extra")
        assertNotNull(extra.deletedAt)
        assertTrue(!extra.pending)
        assertNull(dao.queue().firstOrNull { it.entityId == "extra" })
        assertNotNull(dao.lists.value[derived])
    }

    @Test
    fun ningunaTareaSeQuedaSinLista() = runTest {
        seedLocal()
        backend.seed(SyncEntity.LIST, "lista-del-servidor", mapOf("name" to "Tareas", "position" to 0), at("2026-11-20T10:00:00Z"))

        cycle()
        cycle()

        val live = dao.lists.value.values.filter { it.deletedAt == null }.map { it.id }.toSet()
        assertTrue(dao.tasks.value.values.filter { it.deletedAt == null }.all { it.listId in live })
        assertEquals(2, dao.tasks.value.size)
    }

    @Test
    fun segundoDispositivoMismoUsuario_noDuplica() = runTest {
        seedLocal()
        backend.seed(SyncEntity.LIST, derived, mapOf("name" to "Tareas", "position" to 0), at("2026-11-20T10:00:00Z"))
        backend.seed(SyncEntity.TASK, "del-primer-dispositivo", rig.taskFields("ajena", derived), at("2026-11-20T10:01:00Z"))

        cycle()

        assertEquals(1, backend.records.keys.count { it.first == SyncEntity.LIST })
        assertEquals(setOf(derived), dao.lists.value.keys)
        assertEquals(setOf("t1", "t2", "del-primer-dispositivo"), dao.tasks.value.keys)
    }

    @Test
    fun repetirElPrimerSync_idempotente() = runTest {
        seedLocal()

        cycle()
        val sent = backend.batches.flatten().size
        val tasks = dao.tasks.value
        val lists = dao.lists.value
        cycle()

        assertEquals(sent, backend.batches.flatten().size)
        assertEquals(tasks, dao.tasks.value)
        assertEquals(lists, dao.lists.value)
        assertEquals(3, backend.records.size)
    }
}
