package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.model.Task
import com.simposio.taskeer.domain.model.TaskRepeat
import java.time.DayOfWeek
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMappingTest {

    private val dueAt: Instant = Instant.parse("2026-10-22T08:30:00Z")

    @Test
    fun entidadADominio_mapeaLosDoceCampos() {
        val entity = TaskEntity(
            id = "tarea-1",
            columnId = "columna-1",
            title = "Escribir el informe",
            description = "Con los datos del trimestre",
            priority = "HIGH",
            dueAt = dueAt.toEpochMilli(),
            assigneeId = "local-user",
            position = 4,
            createdAt = 0L,
            updatedAt = dueAt.toEpochMilli(),
            pending = true,
            completedAt = null,
            deletedAt = null,
        )

        val task = entity.toTask()

        assertEquals("tarea-1", task.id)
        assertEquals("columna-1", task.columnId)
        assertEquals("Escribir el informe", task.title)
        assertEquals("Con los datos del trimestre", task.description)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(dueAt, task.dueAt)
        assertEquals("local-user", task.assigneeId)
        assertEquals(4, task.position)
        assertEquals(dueAt, task.sync.updatedAt)
        assertTrue(task.sync.pending)
        assertNull(task.sync.deletedAt)
        assertFalse(task.isDeleted)
    }

    @Test
    fun dominioAEntidad_escribeTodosLosCampos() {
        val task = Task(
            columnId = "columna-1",
            title = "Escribir el informe",
            description = "Con los datos del trimestre",
            priority = TaskPriority.URGENT,
            dueAt = dueAt,
            assigneeId = "local-user",
            position = 4,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata("tarea-1", dueAt, pending = true, deletedAt = null),
        )

        val entity = task.toEntity()

        assertEquals("tarea-1", entity.id)
        assertEquals("columna-1", entity.columnId)
        assertEquals("Escribir el informe", entity.title)
        assertEquals("Con los datos del trimestre", entity.description)
        assertEquals("URGENT", entity.priority)
        assertEquals(dueAt.toEpochMilli(), entity.dueAt)
        assertEquals("local-user", entity.assigneeId)
        assertEquals(4, entity.position)
        assertEquals(dueAt.toEpochMilli(), entity.updatedAt)
        assertTrue(entity.pending)
        assertNull(entity.deletedAt)
    }

    @Test
    fun columnIdNulo_hacePasoIdaYVuelta() {
        val task = Task(
            columnId = null,
            title = "Comprar pan",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = "local-user",
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata("tarea-1", dueAt, pending = true, deletedAt = null),
        )

        val entity = task.toEntity()

        assertNull(entity.columnId)
        assertEquals(task, entity.toTask())
    }

    @Test
    fun idaYVuelta_preservaElInstanteYLosMetadatos() {
        val task = Task(
            columnId = "columna-1",
            title = "Tarea",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            assigneeId = null,
            position = 2,
            createdAt = Instant.EPOCH,
            completedAt = null,
            sync = SyncMetadata("tarea-1", dueAt, pending = true, deletedAt = dueAt),
        )

        val roundTrip = task.toEntity().toTask()

        assertEquals(task, roundTrip)
        assertEquals(dueAt, roundTrip.dueAt)
        assertEquals(dueAt, roundTrip.sync.updatedAt)
        assertEquals(dueAt, roundTrip.sync.deletedAt)
        assertTrue(roundTrip.sync.pending)
        assertTrue(roundTrip.isDeleted)
    }

    @Test
    fun dueAt_admiteNuloYPresente() {
        val sinFecha = TaskEntity(
            id = "tarea-1",
            columnId = "columna-1",
            title = "Tarea",
            description = null,
            priority = "MEDIUM",
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = 0L,
            updatedAt = dueAt.toEpochMilli(),
            pending = true,
            completedAt = null,
            deletedAt = null,
        )

        assertNull(sinFecha.toTask().dueAt)
        assertNull(sinFecha.toTask().toEntity().dueAt)

        val conFecha = sinFecha.copy(dueAt = dueAt.toEpochMilli())

        assertEquals(dueAt, conFecha.toTask().dueAt)
        assertEquals(dueAt.toEpochMilli(), conFecha.toTask().toEntity().dueAt)
    }

    @Test
    fun prioridad_idaYVuelta_preservaElValor() {
        TaskPriority.entries.forEach { priority ->
            val entity = TaskEntity(
                id = "tarea-1",
                columnId = "columna-1",
                title = "Tarea",
                description = null,
                priority = priority.name,
                dueAt = null,
                assigneeId = null,
                position = 0,
                createdAt = 0L,
                updatedAt = dueAt.toEpochMilli(),
                pending = true,
                completedAt = null,
                deletedAt = null,
            )

            assertEquals(priority, entity.toTask().priority)
            assertEquals(priority.name, entity.toTask().toEntity().priority)
        }
    }

    @Test
    fun completedAt_idaYVuelta() {
        val completedAt = Instant.parse("2026-11-15T09:00:00Z")
        val task = Task(
            columnId = null,
            title = "Pan",
            description = null,
            priority = TaskPriority.MEDIUM,
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = Instant.EPOCH,
            completedAt = completedAt,
            sync = SyncMetadata("tarea-1", dueAt, pending = true, deletedAt = null),
        )

        val entity = task.toEntity()

        assertEquals(completedAt.toEpochMilli(), entity.completedAt)
        assertEquals(completedAt, entity.toTask().completedAt)
        assertTrue(entity.toTask().isCompleted)
    }

    @Test
    fun completedAtNulo_idaYVuelta() {
        val entity = TaskEntity(
            id = "tarea-1",
            columnId = null,
            title = "Pan",
            description = null,
            priority = "MEDIUM",
            dueAt = null,
            assigneeId = null,
            position = 0,
            createdAt = 0L,
            completedAt = null,
            updatedAt = 0L,
            pending = false,
            deletedAt = null,
        )

        assertNull(entity.toTask().completedAt)
        assertFalse(entity.toTask().isCompleted)
        assertNull(entity.toTask().toEntity().completedAt)
    }

    @Test
    fun repeat_idaYVuelta() {
        val rules = RepeatUnit.entries.flatMap { unit ->
            listOf(
                TaskRepeat(unit, 3, emptySet(), 31),
                TaskRepeat(unit, 1, if (unit == RepeatUnit.WEEK) setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY) else emptySet(), 5),
            )
        }

        rules.forEach { rule ->
            val task = repeatingTask(rule).copy(repeatSuccessorId = "sucesora")

            val entity = task.toEntity()

            assertEquals(rule.unit.name, entity.repeatUnit)
            assertEquals(rule.interval, entity.repeatInterval)
            assertEquals(rule.anchorDay, entity.repeatAnchorDay)
            assertEquals("sucesora", entity.repeatSuccessorId)
            assertEquals(task, entity.toTask())
        }
    }

    @Test
    fun repeat_mascaraDeDias_usaElBit0ParaElLunes() {
        val rule = TaskRepeat(RepeatUnit.WEEK, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), 1)

        assertEquals(0b1000101, repeatingTask(rule).toEntity().repeatWeekdays)
        assertEquals(0, repeatingTask(rule.copy(weekdays = emptySet())).toEntity().repeatWeekdays)
    }

    @Test
    fun sinRepeticion_idaYVuelta() {
        val task = repeatingTask(null)

        val entity = task.toEntity()

        assertNull(entity.repeatUnit)
        assertNull(entity.repeatInterval)
        assertNull(entity.repeatWeekdays)
        assertNull(entity.repeatAnchorDay)
        assertNull(entity.repeatSuccessorId)
        assertEquals(task, entity.toTask())
    }

    @Test
    fun repeat_unidadDesconocida_seTrataComoSinRepeticion() {
        val entity = repeatingTask(TaskRepeat(RepeatUnit.DAY, 1, emptySet(), 1)).toEntity().copy(repeatUnit = "FORTNIGHT")

        assertNull(entity.toTask().repeat)
    }

    private fun repeatingTask(repeat: TaskRepeat?) = Task(
        columnId = "columna-1",
        title = "Tarea",
        description = null,
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        assigneeId = null,
        position = 0,
        createdAt = Instant.EPOCH,
        completedAt = null,
        repeat = repeat,
        sync = SyncMetadata("tarea-1", dueAt, pending = true, deletedAt = null),
    )
}
