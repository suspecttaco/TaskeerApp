package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.TaskRepeat
import com.simposio.taskeer.domain.task.RescheduleTarget
import com.simposio.taskeer.testing.FakeDestinationTaskRepository
import com.simposio.taskeer.ui.tasks.TASK_REPEAT_NOT_RESCHEDULABLE
import com.simposio.taskeer.ui.tasks.TaskRescheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReschedulerTest {

    private val converter = DateTimeConverter(TimeZoneProvider { ZoneId.of("Europe/Madrid") })
    private val tasks = FakeDestinationTaskRepository()
    private val rescheduler = TaskRescheduler(tasks, converter) { LocalDate.of(2026, 11, 17) }

    @Test
    fun reprograma_actualizaDueAtVíaUpdateTask() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = Instant.parse("2026-11-10T17:30:00Z")))

        val result = rescheduler.reschedule("a", RescheduleTarget.TOMORROW)

        assertTrue(result is DomainResult.Success)
        val (id, draft) = tasks.updates.single()
        assertEquals("a", id)
        assertEquals(Instant.parse("2026-11-18T17:30:00Z"), draft.dueAt)
        assertEquals("Tarea a", draft.title)
        assertEquals(TaskPriority.HIGH, draft.priority)
    }

    @Test
    fun sinFecha_quitaElDueAt() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = Instant.parse("2026-11-10T17:30:00Z")))

        rescheduler.reschedule("a", RescheduleTarget.SOMEDAY)

        assertNull(tasks.updates.single().second.dueAt)
    }

    @Test
    fun rescheduleTo_conservaLaHoraLocalPrevia() = runTest {
        tasks.inbox.value = listOf(task("a", dueAt = Instant.parse("2026-11-10T17:30:00Z")))

        rescheduler.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(Instant.parse("2026-12-01T17:30:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun rescheduleTo_sinFechaPrevia_usa09Locales() = runTest {
        tasks.inbox.value = listOf(task("a"))

        rescheduler.rescheduleTo("a", LocalDate.of(2026, 12, 1))

        assertEquals(Instant.parse("2026-12-01T08:00:00Z"), tasks.updates.single().second.dueAt)
    }

    @Test
    fun tareaNoExiste_publicaError() = runTest {
        val result = rescheduler.reschedule("fantasma", RescheduleTarget.TOMORROW)

        assertEquals("TASK_NOT_FOUND", (result as DomainResult.Failure).error.code)
        assertTrue(tasks.updates.isEmpty())
    }

    @Test
    fun tareaConRepeticion_noSeReprograma() = runTest {
        tasks.inbox.value = listOf(
            task("a", dueAt = Instant.parse("2026-11-10T17:30:00Z"))
                .copy(repeat = TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 10)),
        )

        val result = rescheduler.reschedule("a", RescheduleTarget.TOMORROW)

        assertEquals(TASK_REPEAT_NOT_RESCHEDULABLE, (result as DomainResult.Failure).error.code)
        assertTrue(tasks.updates.isEmpty())
    }

    private fun task(id: String, dueAt: Instant? = null) = Task(
        columnId = null,
        title = "Tarea $id",
        description = null,
        priority = TaskPriority.HIGH,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        sync = SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null),
    )
}
