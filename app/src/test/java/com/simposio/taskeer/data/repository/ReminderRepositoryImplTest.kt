package com.simposio.taskeer.data.repository

import com.simposio.taskeer.data.local.ReminderCandidateRow
import com.simposio.taskeer.data.local.ReminderDao
import com.simposio.taskeer.data.local.ReminderEntity
import com.simposio.taskeer.data.local.ReminderTaskStateRow
import com.simposio.taskeer.data.local.SubtaskEntity
import com.simposio.taskeer.data.local.TaskDao
import com.simposio.taskeer.data.local.TaskEntity
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderStatus
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-11-04T10:00:00Z")
    private val at: Instant = now.plusSeconds(600)

    private val reminderDao = FakeReminderDao()
    private val taskDao = FakeTaskDao().apply { put(taskEntity("t1", title = "Escribir el informe")) }
    private val repository = ReminderRepositoryImpl(reminderDao, taskDao, now = { now })

    private fun repositoryAt(instant: Instant) =
        ReminderRepositoryImpl(reminderDao, taskDao, now = { instant })

    @Test
    fun createReminder_persistePendienteEnUtcYDevuelveElRecordatorio() = runTest {
        val result = repository.createReminder("t1", at)

        val created = (result as DomainResult.Success).value
        val row = reminderDao.rows.value.getValue(created.id)
        assertEquals(ReminderStatus.PENDING, created.status)
        assertEquals("PENDING", row.status)
        assertEquals(at.toEpochMilli(), row.remindAt)
        assertNull(row.sentAt)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertTrue(row.pending)
        assertNull(row.deletedAt)
        assertEquals(36, created.id.length)
    }

    @Test
    fun createReminder_conHoraPasadaOIgual_devuelveValidationSinFila() = runTest {
        listOf(now.minusSeconds(1), now).forEach { remindAt ->
            val error = (repository.createReminder("t1", remindAt) as DomainResult.Failure).error

            assertTrue(error is DomainError.Validation)
            assertEquals("VALIDATION_ERROR", error.code)
        }
        assertTrue(reminderDao.rows.value.isEmpty())
    }

    @Test
    fun createReminder_deTareaInexistenteOBorrada_devuelveNotFoundSinFila() = runTest {
        taskDao.put(taskEntity("borrada", deletedAt = now.toEpochMilli()))

        listOf("no-existe", "borrada").forEach { taskId ->
            val error = (repository.createReminder(taskId, at) as DomainResult.Failure).error

            assertTrue(error is DomainError.NotFound)
            assertEquals("TASK_NOT_FOUND", error.code)
        }
        assertTrue(reminderDao.rows.value.isEmpty())
    }

    @Test
    fun createReminder_conOtroPendiente_devuelveConflictYNoCreaOtro() = runTest {
        repository.createReminder("t1", at)

        val error = (repository.createReminder("t1", at.plusSeconds(60)) as DomainResult.Failure).error

        assertTrue(error is DomainError.Conflict)
        assertEquals("REMINDER_ALREADY_PENDING", error.code)
        assertEquals(1, reminderDao.rows.value.size)
    }

    @Test
    fun createReminder_trasCancelarElAnterior_sePuedeCrearOtro() = runTest {
        val first = (repository.createReminder("t1", at) as DomainResult.Success).value
        repository.cancelReminder(first.id)

        val second = repository.createReminder("t1", at.plusSeconds(60))

        assertTrue(second is DomainResult.Success)
        assertEquals(2, reminderDao.rows.value.size)
    }

    @Test
    fun createReminder_trasDispararseElAnterior_sePuedeCrearOtro() = runTest {
        repository.createReminder("t1", at)
        repositoryAt(at).claimDue(at)

        val second = repositoryAt(at).createReminder("t1", at.plusSeconds(60))

        assertTrue(second is DomainResult.Success)
    }

    @Test
    fun cancelReminder_pasaADismissedYConservaLaFila() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value

        val result = repositoryAt(now.plusSeconds(30)).cancelReminder(created.id)

        assertTrue(result is DomainResult.Success)
        val row = reminderDao.rows.value.getValue(created.id)
        assertEquals("DISMISSED", row.status)
        assertEquals(now.plusSeconds(30).toEpochMilli(), row.updatedAt)
        assertTrue(row.pending)
        assertNull(row.deletedAt)
    }

    @Test
    fun cancelReminder_inexistente_devuelveReminderNotFound() = runTest {
        val error = (repository.cancelReminder("no-existe") as DomainResult.Failure).error

        assertTrue(error is DomainError.NotFound)
        assertEquals("REMINDER_NOT_FOUND", error.code)
    }

    @Test
    fun cancelReminder_noPendiente_devuelveReminderNotPendingYNoCambiaLaFila() = runTest {
        listOf("SENT", "DISMISSED").forEach { status ->
            val row = reminderEntity("r-$status", status = status)
            reminderDao.upsert(row)

            val error = (repository.cancelReminder(row.id) as DomainResult.Failure).error

            assertTrue(error is DomainError.Validation)
            assertEquals("REMINDER_NOT_PENDING", error.code)
            assertEquals(row, reminderDao.rows.value.getValue(row.id))
        }
    }

    @Test
    fun claimDue_venciendoHaceMenosDeUnaHora_loPasaASentYLoDevuelveParaNotificar() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value

        val claim = repositoryAt(at.plusSeconds(30)).claimDue(at.plusSeconds(30))

        assertEquals(listOf("Escribir el informe"), claim.toNotify.map { it.taskTitle })
        assertEquals(listOf(created.id), claim.toNotify.map { it.reminder.id })
        val row = reminderDao.rows.value.getValue(created.id)
        assertEquals("SENT", row.status)
        assertEquals(at.plusSeconds(30).toEpochMilli(), row.sentAt)
    }

    @Test
    fun claimDue_llamadasRepetidas_soloLaPrimeraDevuelveElRecordatorio() = runTest {
        repository.createReminder("t1", at)
        val firing = repositoryAt(at)

        assertEquals(1, firing.claimDue(at).toNotify.size)
        assertTrue(firing.claimDue(at).toNotify.isEmpty())
    }

    @Test
    fun claimDue_deRecordatorioFuturo_noLoToca() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value

        val claim = repository.claimDue(now)

        assertTrue(claim.toNotify.isEmpty() && claim.missed.isEmpty() && claim.discarded.isEmpty())
        assertEquals("PENDING", reminderDao.rows.value.getValue(created.id).status)
    }

    @Test
    fun claimDue_deCanceladoOEnviado_noLoDevuelveNiLoCambia() = runTest {
        val dismissed = reminderEntity("r-d", status = "DISMISSED", remindAt = now.minusSeconds(60).toEpochMilli())
        val sent = reminderEntity("r-s", status = "SENT", remindAt = now.minusSeconds(60).toEpochMilli())
        reminderDao.upsert(dismissed)
        reminderDao.upsert(sent)

        val claim = repository.claimDue(now)

        assertTrue(claim.toNotify.isEmpty() && claim.missed.isEmpty() && claim.discarded.isEmpty())
        assertEquals(dismissed, reminderDao.rows.value.getValue("r-d"))
        assertEquals(sent, reminderDao.rows.value.getValue("r-s"))
    }

    @Test
    fun claimDue_deTareaCompletada_loDescartaConDismissedSinAviso() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value
        taskDao.doneColumns += "c1"

        val claim = repositoryAt(at).claimDue(at)

        assertTrue(claim.toNotify.isEmpty())
        assertEquals(listOf(created.id), claim.discarded.map { it.id })
        assertEquals("DISMISSED", reminderDao.rows.value.getValue(created.id).status)
    }

    @Test
    fun claimDue_deTareaBorrada_loDescartaConDismissedSinAviso() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value
        taskDao.put(taskEntity("t1", title = "Escribir el informe", deletedAt = now.toEpochMilli()))

        val claim = repositoryAt(at).claimDue(at)

        assertTrue(claim.toNotify.isEmpty())
        assertEquals(listOf(created.id), claim.discarded.map { it.id })
        assertEquals("DISMISSED", reminderDao.rows.value.getValue(created.id).status)
    }

    @Test
    fun claimDue_venciendoHaceUnaHoraOMas_loPasaASentConSentAtAhoraSinAviso() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value
        val late = at.plus(Duration.ofHours(1))

        val claim = repositoryAt(late).claimDue(late)

        assertTrue(claim.toNotify.isEmpty())
        assertEquals(listOf(created.id), claim.missed.map { it.id })
        val row = reminderDao.rows.value.getValue(created.id)
        assertEquals("SENT", row.status)
        assertEquals(late.toEpochMilli(), row.sentAt)
    }

    @Test
    fun claimDue_venciendoHaceMenosDeUnaHoraPorUnSegundo_sigueNotificando() = runTest {
        repository.createReminder("t1", at)
        val almostLate = at.plus(Duration.ofHours(1)).minusSeconds(1)

        val claim = repositoryAt(almostLate).claimDue(almostLate)

        assertEquals(1, claim.toNotify.size)
        assertTrue(claim.missed.isEmpty())
    }

    @Test
    fun claimDue_vencidoYCompletado_prevaleceDismissedSobreElOmitidoPorTolerancia() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value
        taskDao.doneColumns += "c1"
        val late = at.plus(Duration.ofHours(2))

        val claim = repositoryAt(late).claimDue(late)

        assertTrue(claim.missed.isEmpty())
        assertEquals(listOf(created.id), claim.discarded.map { it.id })
        assertEquals("DISMISSED", reminderDao.rows.value.getValue(created.id).status)
    }

    @Test
    fun claimDue_tareaCompletadaQueVuelveANoCompletadaAntesDeLaHora_sigueVigenteYAvisa() = runTest {
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value
        taskDao.doneColumns += "c1"
        assertEquals("PENDING", reminderDao.rows.value.getValue(created.id).status)
        taskDao.doneColumns -= "c1"

        val claim = repositoryAt(at).claimDue(at)

        assertEquals(listOf(created.id), claim.toNotify.map { it.reminder.id })
    }

    @Test
    fun nextPendingAt_devuelveElMenorRemindAtFuturoEstricto() = runTest {
        reminderDao.upsert(reminderEntity("pasado", remindAt = now.minusSeconds(5).toEpochMilli()))
        reminderDao.upsert(reminderEntity("ahora", remindAt = now.toEpochMilli()))
        reminderDao.upsert(reminderEntity("cerca", remindAt = now.plusSeconds(30).toEpochMilli()))
        reminderDao.upsert(reminderEntity("lejos", remindAt = now.plusSeconds(900).toEpochMilli()))
        reminderDao.upsert(
            reminderEntity("enviado", status = "SENT", remindAt = now.plusSeconds(10).toEpochMilli()),
        )

        assertEquals(now.plusSeconds(30), repository.nextPendingAt(now))
    }

    @Test
    fun nextPendingAt_sinPendientesFuturos_devuelveNull() = runTest {
        assertNull(repository.nextPendingAt(now))
    }

    @Test
    fun observeReminder_emiteElPendienteYNullTrasCancelar() = runTest {
        assertNull(repository.observeReminder("t1").first())
        val created = (repository.createReminder("t1", at) as DomainResult.Success).value

        assertEquals(created.id, repository.observeReminder("t1").first()?.id)
        repository.cancelReminder(created.id)

        assertNull(repository.observeReminder("t1").first())
    }

    @Test
    fun observePending_listaSoloLosPendientes() = runTest {
        reminderDao.upsert(reminderEntity("p1"))
        reminderDao.upsert(reminderEntity("s1", status = "SENT"))
        reminderDao.upsert(reminderEntity("d1", status = "DISMISSED"))

        assertEquals(listOf("p1"), repository.observePending().first().map { it.id })
    }

    private fun reminderEntity(
        id: String,
        status: String = "PENDING",
        remindAt: Long = at.toEpochMilli(),
    ) = ReminderEntity(
        id = id,
        taskId = "t1",
        remindAt = remindAt,
        status = status,
        sentAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        pending = false,
        deletedAt = null,
    )

    private fun taskEntity(id: String, title: String = "Tarea", deletedAt: Long? = null) = TaskEntity(
        id = id,
        columnId = "c1",
        title = title,
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = null,
        position = 0,
        createdAt = 0L,
        updatedAt = 0L,
        pending = false,
        deletedAt = deletedAt,
    )

    private class FakeReminderDao : ReminderDao() {
        val rows = MutableStateFlow<Map<String, ReminderEntity>>(emptyMap())

        override fun observePendingByTask(taskId: String): Flow<ReminderEntity?> =
            rows.map { all -> all.values.firstOrNull { it.taskId == taskId && it.status == "PENDING" } }

        override fun observeAllPending(): Flow<List<ReminderEntity>> = rows.map { all ->
            all.values.filter { it.status == "PENDING" }.sortedWith(compareBy({ it.remindAt }, { it.id }))
        }

        override suspend fun findPendingByTask(taskId: String): ReminderEntity? =
            rows.value.values.firstOrNull { it.taskId == taskId && it.status == "PENDING" }

        override suspend fun findById(id: String): ReminderEntity? = rows.value[id]

        override suspend fun findPendingUpTo(limit: Long): List<ReminderEntity> =
            rows.value.values
                .filter { it.status == "PENDING" && it.remindAt <= limit }
                .sortedWith(compareBy({ it.remindAt }, { it.id }))

        override suspend fun minPendingAfter(after: Long): Long? =
            rows.value.values
                .filter { it.status == "PENDING" && it.remindAt > after }
                .minOfOrNull { it.remindAt }

        override suspend fun upsert(reminder: ReminderEntity) {
            rows.value = rows.value + (reminder.id to reminder)
        }

        override suspend fun markSent(id: String, now: Long): Int = transition(id, "SENT", now)

        override suspend fun markDismissed(id: String, now: Long): Int = transition(id, "DISMISSED", now)

        private suspend fun transition(id: String, status: String, now: Long): Int {
            val row = rows.value[id]?.takeIf { it.status == "PENDING" } ?: return 0
            upsert(
                row.copy(
                    status = status,
                    sentAt = if (status == "SENT") now else row.sentAt,
                    updatedAt = now,
                    pending = true,
                ),
            )
            return 1
        }
    }

    private class FakeTaskDao : TaskDao() {
        private val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

        val doneColumns: MutableSet<String> = mutableSetOf()

        fun put(task: TaskEntity) {
            rows.value = rows.value + (task.id to task)
        }

        override fun observeByBoard(boardId: String): Flow<List<TaskEntity>> = MutableStateFlow(emptyList())

        override suspend fun findActiveByColumn(columnId: String): List<TaskEntity> = emptyList()

        override suspend fun findById(id: String): TaskEntity? = rows.value[id]

        override suspend fun countByColumn(columnId: String): Int = 0

        override suspend fun upsert(task: TaskEntity) = put(task)

        override suspend fun upsertAll(tasks: List<TaskEntity>) = Unit

        override suspend fun softDeleteSubtasksOfTask(taskId: String, deletedAt: Long) = Unit

        override fun observeReminderCandidates(): Flow<List<ReminderCandidateRow>> = emptyFlow()

        override suspend fun countActive(): Int = 0

        override suspend fun findReminderState(taskId: String): ReminderTaskStateRow? =
            rows.value[taskId]?.let {
                ReminderTaskStateRow(it.title, it.deletedAt != null, it.columnId in doneColumns)
            }

        override suspend fun dismissRemindersOfTask(taskId: String, now: Long): Int = 0

        override suspend fun insertSubtasks(subtasks: List<SubtaskEntity>) = Unit

        override suspend fun insertReminder(reminder: ReminderEntity) = Unit
    }
}
