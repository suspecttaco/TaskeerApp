package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskMappingTest {

    private val at: Instant = Instant.parse("2026-10-22T08:30:00Z")

    @Test
    fun toSubtask_yToEntity_sonSimetricos() {
        val entity = SubtaskEntity(
            id = "sub-1",
            taskId = "t1",
            title = "Reunir datos",
            completed = true,
            position = 2,
            assigneeId = "local-user",
            dueAt = at.plusSeconds(5).toEpochMilli(),
            createdAt = at.minusSeconds(60).toEpochMilli(),
            updatedAt = at.toEpochMilli(),
            pending = true,
            deletedAt = at.toEpochMilli(),
        )

        val subtask = entity.toSubtask()

        assertEquals(
            Subtask(
                taskId = "t1",
                title = "Reunir datos",
                completed = true,
                position = 2,
                assigneeId = "local-user",
                dueAt = at.plusSeconds(5),
                createdAt = at.minusSeconds(60),
                sync = SyncMetadata("sub-1", at, pending = true, deletedAt = at),
            ),
            subtask,
        )
        assertEquals(entity, subtask.toEntity())
        assertTrue(subtask.isDeleted)
    }

    @Test
    fun sinAsignadoNiFecha_idaYVueltaConservaNulos() {
        val subtask = Subtask(
            taskId = "t1",
            title = "A",
            completed = false,
            position = 0,
            createdAt = at,
            sync = SyncMetadata("sub-2", at, pending = false, deletedAt = null),
        )

        val roundTrip = subtask.toEntity().toSubtask()

        assertNull(roundTrip.assigneeId)
        assertNull(roundTrip.dueAt)
        assertEquals(subtask, roundTrip)
    }
}
