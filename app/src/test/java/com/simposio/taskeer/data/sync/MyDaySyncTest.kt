package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.DISMISSED_ADDED_AT
import com.simposio.taskeer.domain.date.dayWindowAt
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncMyDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class MyDaySyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val earlier = at("2026-11-25T09:00:00Z")
    private val later = at("2026-11-26T08:00:00Z")

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    private fun myDayMutations() = backend.batches.flatten().filter { it.entity == SyncEntity.MY_DAY }

    private fun serverMark(taskId: String, addedAt: Instant, deletedAt: Instant? = null) {
        backend.seed(SyncEntity.MY_DAY, taskId, mapOf("taskId" to taskId, "addedAt" to addedAt.toString()), earlier, deletedAt)
    }

    @Test
    fun marcaNueva_upsertConAddedAt() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncMyDay("t1", addedAt = later.toEpochMilli()))

        cycle()

        val sent = myDayMutations().single()
        assertEquals(SyncOp.UPSERT, sent.op)
        assertEquals("t1", sent.id)
        assertEquals(mapOf("addedAt" to "2026-11-26T08:00:00.000Z"), sent.data)
        assertFalse(dao.myDay.value.getValue("t1").pending)
        assertNotNull(dao.baseline("myDay", "t1"))
    }

    @Test
    fun renovarMarca_addedAtMayor() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        serverMark("t1", earlier)
        dao.put(syncMyDay("t1", addedAt = earlier.toEpochMilli(), updatedAt = earlier.toEpochMilli(), pending = false))
        dao.baseline("myDay", "t1", earlier.toEpochMilli())
        dao.put(syncMyDay("t1", addedAt = later.toEpochMilli(), updatedAt = later.toEpochMilli()))

        cycle()

        assertEquals("2026-11-26T08:00:00.000Z", myDayMutations().single().data?.get("addedAt"))
        assertEquals("2026-11-26T08:00:00.000Z", backend.records.getValue(SyncEntity.MY_DAY to "t1").fields["addedAt"])
        assertEquals(later.toEpochMilli(), dao.myDay.value.getValue("t1").addedAt)
    }

    @Test
    fun quitar_delete() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        serverMark("t1", earlier)
        dao.baseline("myDay", "t1", earlier.toEpochMilli())
        dao.put(syncMyDay("t1", addedAt = earlier.toEpochMilli(), updatedAt = later.toEpochMilli(), deletedAt = later.toEpochMilli()))

        cycle()

        assertEquals(SyncOp.DELETE, myDayMutations().single().op)
        assertNotNull(backend.records.getValue(SyncEntity.MY_DAY to "t1").deletedAt)
        assertFalse(dao.myDay.value.getValue("t1").pending)
    }

    @Test
    fun sugerenciaDescartada_noSeEnvia() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncMyDay("t1", addedAt = DISMISSED_ADDED_AT, updatedAt = later.toEpochMilli(), deletedAt = later.toEpochMilli()))

        cycle()

        assertTrue(myDayMutations().isEmpty())
        assertNull(backend.records[SyncEntity.MY_DAY to "t1"])
        assertTrue(dao.queueRows.value.values.none { it.entity == "myDay" })
        assertFalse(dao.myDay.value.getValue("t1").pending)
        assertEquals(DISMISSED_ADDED_AT, dao.myDay.value.getValue("t1").addedAt)
    }

    @Test
    fun pullDeMarca_guardaAddedAtDelServidor() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        serverMark("t1", later)

        cycle()

        val row = dao.myDay.value.getValue("t1")
        assertEquals(later.toEpochMilli(), row.addedAt)
        assertNull(row.deletedAt)
        assertFalse(row.pending)
        assertTrue(myDayMutations().isEmpty())
    }

    @Test
    fun hoyDerivadoDeAddedAt() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        rig.syncedTaskOnServer("t2")
        dao.put(syncMyDay("t1", addedAt = later.toEpochMilli()))
        serverMark("t2", earlier)

        cycle()

        assertEquals(setOf("addedAt"), myDayMutations().single().data?.keys)
        val dayStart = dayWindowAt(rig.now, ZoneOffset.UTC).start.toEpochMilli()
        assertTrue(dao.myDay.value.getValue("t1").addedAt >= dayStart)
        assertTrue(dao.myDay.value.getValue("t2").addedAt < dayStart)
    }

    @Test
    fun snapshotSinLaMarcaSincronizada_laDaPorQuitada() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncMyDay("t1", addedAt = earlier.toEpochMilli(), updatedAt = earlier.toEpochMilli(), pending = false))
        dao.baseline("myDay", "t1", earlier.toEpochMilli())

        cycle()

        assertEquals(rig.now.toEpochMilli(), dao.myDay.value.getValue("t1").deletedAt)
        assertTrue(myDayMutations().isEmpty())
    }

    @Test
    fun tombstoneDelServidor_noConvierteUnaDescartadaEnQuitada() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncMyDay("t1", addedAt = DISMISSED_ADDED_AT, updatedAt = earlier.toEpochMilli(), pending = false, deletedAt = earlier.toEpochMilli()))
        dao.baseline("myDay", "t1", earlier.toEpochMilli())
        serverMark("t1", later, deletedAt = later)

        cycle()

        assertEquals(DISMISSED_ADDED_AT, dao.myDay.value.getValue("t1").addedAt)
    }

    @Test
    fun marcaPendiente_cuentaEnElIndicadorYLaDescartadaNo() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        rig.syncedTaskOnServer("t2")
        dao.put(syncMyDay("t1", addedAt = later.toEpochMilli()))
        dao.put(syncMyDay("t2", addedAt = DISMISSED_ADDED_AT, deletedAt = later.toEpochMilli()))

        assertEquals(1, rig.store.observePendingCount().first())
    }
}
