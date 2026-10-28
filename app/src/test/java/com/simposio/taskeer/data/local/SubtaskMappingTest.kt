package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SyncMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
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
            updatedAt = at.toEpochMilli(),
            pending = true,
            deletedAt = at.toEpochMilli(),
        )

        val subtask = entity.toSubtask()

        assertEquals(
            Subtask("t1", "Reunir datos", true, 2, SyncMetadata("sub-1", at, pending = true, deletedAt = at)),
            subtask,
        )
        assertEquals(entity, subtask.toEntity())
        assertTrue(subtask.isDeleted)
    }
}
