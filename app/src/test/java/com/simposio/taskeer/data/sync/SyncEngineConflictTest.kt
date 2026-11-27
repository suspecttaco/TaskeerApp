package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.QUEUE_STATE_STUCK
import com.simposio.taskeer.data.local.SyncQueueEntity
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncLoss
import com.simposio.taskeer.domain.sync.SyncNotice
import com.simposio.taskeer.domain.sync.SyncTimestamps
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineConflictTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val base = at("2026-11-26T10:00:00Z")
    private val serverEdit = at("2026-11-26T12:00:00Z")

    // Fila local con cambios sin enviar sobre una tarea que el servidor conocía en `base`.
    private suspend fun localEdit(localUpdatedAt: String, title: String = "local", deleted: Boolean = false) {
        rig.bound()
        val updated = at(localUpdatedAt).toEpochMilli()
        dao.put(syncTask("t1", listId = rig.listId, title = title, updatedAt = updated, deletedAt = if (deleted) updated else null))
        dao.baseline("task", "t1", base.toEpochMilli())
        rig.store.collect(rig.now)
    }

    private suspend fun pull(vararg records: ServerRecord): PageEffect =
        rig.store.applyPage(rig.page(records.toList()), USER_A, rig.now, PageCommit.SaveCursor)

    @Test
    fun pullSobrePending_localMasReciente_gana() = runTest {
        localEdit("2026-11-26T13:00:00Z")

        val effect = pull(rig.taskRecord("t1", "servidor", serverEdit))

        val row = dao.tasks.value.getValue("t1")
        assertEquals("local", row.title)
        assertTrue(row.pending)
        assertEquals(serverEdit.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
        assertEquals(1, dao.queue().size)
        assertTrue(effect.losses.isEmpty())
    }

    @Test
    fun pullSobrePending_servidorMasReciente_gana() = runTest {
        localEdit("2026-11-26T11:00:00Z")

        val effect = pull(rig.taskRecord("t1", "servidor", serverEdit))

        val row = dao.tasks.value.getValue("t1")
        assertEquals("servidor", row.title)
        assertFalse(row.pending)
        assertEquals(serverEdit.toEpochMilli(), row.updatedAt)
        assertEquals(0, dao.queue().size)
        assertEquals(listOf(SyncLoss.REPLACED_BY_SERVER), effect.losses)
    }

    @Test
    fun tombstoneSobreEdicionLocal_descartaYAvisa() = runTest {
        localEdit("2026-11-26T18:00:00Z")
        // Una entrada atascada no se envía: la edición sigue pendiente cuando llega el tombstone en la traída.
        val entry = dao.queue().single()
        dao.upsertQueue(entry.stuck())
        val notices = mutableListOf<SyncNotice>()
        val engine = with(rig) { engine() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.notices.collect { notices += it } }
        rig.seedTask("t1", "x", serverEdit, deletedAt = serverEdit)

        engine.runCycle(manual = true)

        val row = dao.tasks.value.getValue("t1")
        assertEquals(serverEdit.toEpochMilli(), row.deletedAt)
        assertFalse(row.pending)
        assertEquals(0, dao.queue().size)
        assertEquals(mapOf(SyncLoss.DELETED_BY_SERVER to 1), notices.single().counts)
    }

    @Test
    fun borradoLocalFrenteEdicionRemota_porInstante() = runTest {
        localEdit("2026-11-26T13:00:00Z", deleted = true)

        pull(rig.taskRecord("t1", "servidor", serverEdit))

        assertEquals(at("2026-11-26T13:00:00Z").toEpochMilli(), dao.tasks.value.getValue("t1").deletedAt)
        assertTrue(dao.tasks.value.getValue("t1").pending)
    }

    @Test
    fun borradoLocalMasViejoQueLaEdicionRemota_ganaElServidor() = runTest {
        localEdit("2026-11-26T11:00:00Z", deleted = true)

        pull(rig.taskRecord("t1", "servidor", serverEdit))

        val revived = dao.tasks.value.getValue("t1")
        assertNull(revived.deletedAt)
        assertEquals("servidor", revived.title)
        assertFalse(revived.pending)
    }

    @Test
    fun noOp_noGeneraConflicto() = runTest {
        localEdit("2026-11-26T11:00:00Z", title = "Comprar leche")

        val effect = pull(ServerRecord(SyncEntity.TASK, "t1", serverEdit, null, rig.taskFields("Comprar leche")))

        val row = dao.tasks.value.getValue("t1")
        assertTrue(row.pending)
        assertEquals(at("2026-11-26T11:00:00Z").toEpochMilli(), row.updatedAt)
        assertTrue(effect.losses.isEmpty())
        assertEquals(serverEdit.toEpochMilli(), dao.baseline("task", "t1")!!.serverUpdatedAt)
    }

    // Mismo veredicto que el servidor para V07-V10, V12 y V13; V11 (tombstone sin resurrección) lo cubren tombstoneSobreEdicionLocal_descartaYAvisa y versionVieja_noRevivefilaBorrada.
    @Test
    fun coincideConElServidor_vectoresV07aV10YV12V13() = runTest {
        for (name in listOf("V07", "V08", "V09", "V10", "V12", "V13")) {
            val cases = JSONObject(requireNotNull(javaClass.getResource("/sync-contract/v1/$name.json")).readText()).getJSONArray("cases")
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val label = "$name: ${case.getString("name")}"
                val server = case.getJSONObject("server")
                val mutation = case.getJSONObject("mutation")
                val serverFields = plain(server.getJSONObject("fields"))
                val noOp = mutation.has("data") && plain(mutation.getJSONObject("data")) == serverFields
                val local = SyncRig(now = SyncTimestamps.parse(case.getString("serverNow"))!!)
                local.bound()
                val localUpdated = SyncTimestamps.parse(mutation.getString("clientUpdatedAt"))!!.toEpochMilli()
                val deleted = mutation.getString("op") == "delete"
                local.dao.put(taskFrom(if (deleted) serverFields else plain(mutation.getJSONObject("data")), localUpdated, deleted))
                local.dao.baseline("task", "t1", SyncTimestamps.parse(mutation.getString("baseUpdatedAt"))!!.toEpochMilli())
                local.store.collect(local.now)
                val record = ServerRecord(
                    SyncEntity.TASK, "t1", SyncTimestamps.parse(server.getString("updatedAt"))!!,
                    if (server.isNull("deletedAt")) null else SyncTimestamps.parse(server.getString("deletedAt")), serverFields,
                )

                val effect = local.store.applyPage(local.page(listOf(record)), USER_A, local.now, PageCommit.SaveCursor)

                val row = local.dao.tasks.value.getValue("t1")
                val localKept = row.pending
                assertEquals(label, case.getJSONObject("expect").getString("status") == "applied", localKept)
                if (noOp) assertTrue(label, effect.losses.isEmpty())
                if (!localKept) assertEquals(label, serverFields["title"], row.title)
            }
        }
    }

    private fun SyncQueueEntity.stuck() = copy(state = QUEUE_STATE_STUCK)

    private fun plain(value: JSONObject): Map<String, Any?> =
        value.keys().asSequence().toList().sorted().associateWith { key -> value.get(key).takeUnless { it == JSONObject.NULL } }

    private fun taskFrom(fields: Map<String, Any?>, updatedAt: Long, deleted: Boolean) = TaskEntity(
        id = "t1",
        columnId = fields["columnId"] as String?,
        title = fields["title"] as String,
        description = fields["description"] as String?,
        priority = fields["priority"] as String,
        dueAt = null,
        assigneeId = null,
        position = fields["position"] as Int,
        createdAt = 0L,
        completedAt = null,
        updatedAt = updatedAt,
        pending = true,
        deletedAt = if (deleted) updatedAt else null,
        listId = fields["listId"] as String?,
    )
}
