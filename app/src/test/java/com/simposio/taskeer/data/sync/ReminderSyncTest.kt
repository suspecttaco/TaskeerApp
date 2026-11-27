package com.simposio.taskeer.data.sync

import com.simposio.taskeer.data.local.toEntity
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.Reminder
import com.simposio.taskeer.domain.model.ReminderStatus
import com.simposio.taskeer.domain.reminder.snoozed
import com.simposio.taskeer.domain.sync.SyncEntity
import com.simposio.taskeer.domain.sync.SyncOp
import com.simposio.taskeer.testing.SyncRig
import com.simposio.taskeer.testing.at
import com.simposio.taskeer.testing.syncReminder
import com.simposio.taskeer.testing.syncTask
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSyncTest {

    private val rig = SyncRig()
    private val dao get() = rig.dao
    private val backend get() = rig.backend
    private val remindAt = at("2026-11-27T08:00:00Z")

    private suspend fun TestScope.cycle(onChanged: suspend () -> Unit = {}) =
        with(rig) { engine(onRemindersChanged = onChanged).runCycle(manual = true) }

    private fun reminderMutations() = backend.batches.flatten().filter { it.entity == SyncEntity.REMINDER }

    private fun serverReminder(id: String, status: String, sentAt: String? = null, updated: String = "2026-11-26T10:00:00Z") {
        backend.seed(
            SyncEntity.REMINDER, id,
            mapOf("taskId" to "t1", "remindAt" to "2026-11-27T08:00:00.000Z", "status" to status, "sentAt" to sentAt, "origin" to "MOBILE"),
            at(updated),
        )
    }

    @Test
    fun altaManual_upsert() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncReminder("r1", "t1", remindAt.toEpochMilli()))

        cycle()

        val sent = reminderMutations().single()
        assertEquals(SyncOp.UPSERT, sent.op)
        assertEquals(
            mapOf("taskId" to "t1", "remindAt" to "2026-11-27T08:00:00.000Z", "status" to "PENDING", "sentAt" to null),
            sent.data,
        )
        assertEquals("MOBILE", backend.records.getValue(SyncEntity.REMINDER to "r1").fields["origin"])
        assertFalse(dao.reminders.value.getValue("r1").pending)
    }

    @Test
    fun posponer_creaNuevoPendingYOriginalSent() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        val sentAt = at("2026-11-27T08:00:05Z")
        val original = Reminder(
            "t1", remindAt, ReminderStatus.SENT, sentAt, at("2026-11-26T10:00:00Z"),
            com.simposio.taskeer.domain.model.newSyncMetadata(sentAt, "r-original"),
        )
        val next = (original.snoozed(at("2026-11-27T09:00:00Z"), sentAt, clientId = "r-snoozed") as DomainResult.Success).value
        dao.put(original.toEntity().copy(updatedAt = sentAt.toEpochMilli()))
        dao.put(next.toEntity())

        cycle()

        assertEquals(setOf("r-original", "r-snoozed"), reminderMutations().map { it.id }.toSet())
        assertEquals("SENT", backend.records.getValue(SyncEntity.REMINDER to "r-original").fields["status"])
        assertEquals("PENDING", backend.records.getValue(SyncEntity.REMINDER to "r-snoozed").fields["status"])
        assertEquals("r-snoozed", dao.reminders.value.values.single { it.status == "PENDING" }.id)
    }

    @Test
    fun pullDeRecordatorio_notificaAlConciliador() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        serverReminder("r1", "PENDING")
        var notified = 0

        cycle { notified++ }

        assertEquals(1, notified)
        val row = dao.reminders.value.getValue("r1")
        assertEquals("PENDING", row.status)
        assertEquals(remindAt.toEpochMilli(), row.remindAt)
        assertFalse(row.pending)
    }

    @Test
    fun pullSinRecordatorios_noNotificaAlConciliador() = runTest {
        rig.boundWithPersonalData()
        rig.seedTask("t9", "otra", at("2026-11-26T10:00:00Z"))
        var notified = 0

        cycle { notified++ }

        assertEquals(0, notified)
    }

    @Test
    fun sentNoVuelveAPending() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncReminder("r1", "t1", remindAt.toEpochMilli(), status = "SENT", sentAt = remindAt.toEpochMilli(), updatedAt = 5L, pending = false))
        dao.baseline("reminder", "r1", 5L)
        serverReminder("r1", "PENDING", updated = "2026-11-26T11:00:00Z")

        cycle()

        assertEquals("SENT", dao.reminders.value.getValue("r1").status)
        assertTrue(reminderMutations().isEmpty())
    }

    @Test
    fun recordatorioBorradoEnServidor_dejaDeEstarPendiente() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        dao.put(syncReminder("r1", "t1", remindAt.toEpochMilli(), updatedAt = 5L, pending = false))
        dao.baseline("reminder", "r1", 5L)
        backend.seed(
            SyncEntity.REMINDER, "r1",
            mapOf("taskId" to "t1", "remindAt" to "2026-11-27T08:00:00.000Z", "status" to "PENDING"),
            at("2026-11-26T11:00:00Z"), deletedAt = at("2026-11-26T11:00:00Z"),
        )

        cycle()

        val row = dao.reminders.value.getValue("r1")
        assertEquals("DISMISSED", row.status)
        assertEquals(at("2026-11-26T11:00:00Z").toEpochMilli(), row.deletedAt)
    }

    @Test
    fun derivadosYHistorial_noSeEnvian() = runTest {
        rig.boundWithPersonalData()
        dao.put(syncTask("t1", listId = rig.listId, pending = false, updatedAt = 5L).copy(dueAt = remindAt.toEpochMilli()))
        dao.baseline("task", "t1", 5L)

        cycle()

        assertTrue(reminderMutations().isEmpty())
        assertNull(backend.records.keys.firstOrNull { it.first == SyncEntity.REMINDER })
        assertTrue(dao.reminders.value.isEmpty())
        assertTrue(SyncEntity.entries.none { it.wireName.contains("notification", ignoreCase = true) })
    }

    private fun seedTaskWith(overrides: Map<String, Any?>, deletedAt: java.time.Instant? = null, updated: String = "2026-11-26T12:00:00Z") =
        backend.seed(SyncEntity.TASK, "t1", rig.taskFields("Tarea t1") + overrides, at(updated), deletedAt)

    @Test
    fun primeraSync_soloMarcaPendientesLosRecordatoriosPENDING() = runTest {
        dao.put(syncReminder("rp", "t1", remindAt.toEpochMilli(), pending = false))
        dao.put(syncReminder("rs", "t1", remindAt.toEpochMilli(), status = "SENT", sentAt = remindAt.toEpochMilli(), pending = false))
        dao.put(syncReminder("rd", "t1", remindAt.toEpochMilli(), status = "DISMISSED", pending = false))

        dao.markUnsyncedRemindersPending()

        assertEquals(setOf("rp"), dao.reminders.value.values.filter { it.pending }.map { it.id }.toSet())
    }

    @Test
    fun primeraSync_posponerSigueSubiendoLaNuevaYElSentConLineaBaseNoCambia() = runTest {
        dao.put(syncReminder("r-original", "t1", remindAt.toEpochMilli(), status = "SENT", sentAt = remindAt.toEpochMilli(), updatedAt = 5L, pending = false))
        dao.baseline("reminder", "r-original", 5L)
        dao.put(syncReminder("r-snoozed", "t1", remindAt.toEpochMilli() + 3_600_000L, pending = false))

        dao.markUnsyncedRemindersPending()

        assertTrue(dao.reminders.value.getValue("r-snoozed").pending)
        assertFalse(dao.reminders.value.getValue("r-original").pending)
    }

    @Test
    fun pullDeTarea_conOtraDueAt_notificaAlConciliador() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        seedTaskWith(mapOf("dueAt" to "2026-11-28T08:00:00.000Z"))
        var notified = 0

        cycle { notified++ }

        assertEquals(1, notified)
        assertEquals(at("2026-11-28T08:00:00Z").toEpochMilli(), rig.taskRow("t1")?.dueAt)
    }

    @Test
    fun pullDeTarea_completadaOBorrada_notificaAlConciliador() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        seedTaskWith(mapOf("completedAt" to "2026-11-26T12:00:00.000Z"))
        var completed = 0
        cycle { completed++ }
        assertEquals(1, completed)

        seedTaskWith(emptyMap(), deletedAt = at("2026-11-26T13:00:00Z"), updated = "2026-11-26T13:00:00Z")
        var deleted = 0
        cycle { deleted++ }
        assertEquals(1, deleted)
    }

    @Test
    fun pullDeTarea_soloTituloOTarea_sinCambioDeAlarma_noNotifica() = runTest {
        rig.boundWithPersonalData()
        rig.syncedTaskOnServer("t1")
        backend.seed(SyncEntity.TASK, "t1", rig.taskFields("Otro título"), at("2026-11-26T12:00:00Z"))
        var notified = 0

        cycle { notified++ }

        assertEquals(0, notified)
        assertEquals("Otro título", rig.taskRow("t1")?.title)
    }
}
