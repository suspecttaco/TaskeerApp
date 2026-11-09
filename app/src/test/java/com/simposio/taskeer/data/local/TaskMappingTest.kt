package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Task
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
                deletedAt = null,
            )

            assertEquals(priority, entity.toTask().priority)
            assertEquals(priority.name, entity.toTask().toEntity().priority)
        }
    }
}
