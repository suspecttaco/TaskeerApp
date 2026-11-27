package com.simposio.taskeer.data.sync

import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.testing.MainSources
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.USER_A
import com.simposio.taskeer.testing.USER_B
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssigneeSyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val updated = at("2026-11-26T10:00:00Z")

    private suspend fun TestScope.cycle() = with(rig) { engine().runCycle(manual = true) }

    private fun assigned(id: String, assignee: String) =
        ServerRecord(SyncEntity.TASK, id, updated, null, rig.taskFields(id) + mapOf("assigneeId" to assignee))

    @Test
    fun pullAsignadaAMi_aLocalUser_sinNotificacionLocal() = runTest {
        rig.bound()
        val effect = rig.store.applyPage(rig.page(listOf(assigned("t1", USER_A))), USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(LocalUser.ID, dao.tasks.value.getValue("t1").assigneeId)
        assertTrue(effect.losses.isEmpty())
        // El aviso «te asignaron» lo crea el servidor (P8): ningún código de sincronización emite eventos de notificación.
        val offenders = MainSources.kotlinFiles("data/sync").filter { file ->
            file.readLines().any { it.startsWith("import ") && (it.contains(".notification.") || it.contains("ReminderNotifier") || it.contains("NotificationEvent")) }
        }
        assertTrue(offenders.map { it.name }.toString(), offenders.isEmpty())
        assertTrue(dao.wipedBusinessTables.none { it == "app_notifications" })
    }

    @Test
    fun pullAsignadaAOtro_seConserva() = runTest {
        rig.bound()

        rig.store.applyPage(rig.page(listOf(assigned("t2", USER_B))), USER_A, rig.now, PageCommit.SaveCursor)

        assertEquals(USER_B, dao.tasks.value.getValue("t2").assigneeId)
        assertEquals(false, dao.tasks.value.getValue("t2").pending)
    }

    @Test
    fun envio_sustituyeLocalUserPorElIdDelServidorYConservaAjenos() = runTest {
        rig.bound()
        dao.put(syncTask("t1", listId = rig.listId, assigneeId = LocalUser.ID))
        dao.put(syncTask("t2", listId = rig.listId, assigneeId = USER_B))

        cycle()

        val sent = rig.backend.batches.flatten().filter { it.entity == SyncEntity.TASK }.associate { it.id to it.data?.get("assigneeId") }
        assertEquals(mapOf("t1" to USER_A, "t2" to USER_B), sent)
        assertEquals(LocalUser.ID, dao.tasks.value.getValue("t1").assigneeId)
    }
}
