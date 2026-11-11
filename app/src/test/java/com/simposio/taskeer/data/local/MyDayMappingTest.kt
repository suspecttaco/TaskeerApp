package com.simposio.taskeer.data.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyDayMappingTest {

    private val addedAt: Instant = Instant.parse("2026-10-22T08:30:00Z")

    @Test
    fun filaSinTablero_mapeaConBoardNameNulo() {
        val row = MyDayRow(taskEntity(columnId = null), boardId = null, boardName = null, addedAt = addedAt.toEpochMilli())

        val mapped = row.toMyDayTask()

        assertNull(mapped.boardName)
        assertNull(mapped.boardId)
        assertNull(mapped.task.columnId)
        assertEquals(addedAt, mapped.addedAt)
    }

    @Test
    fun filaConTablero_conservaNombreEId() {
        val row = MyDayRow(taskEntity(columnId = "c1"), boardId = "b1", boardName = "Proyecto", addedAt = null)

        val mapped = row.toMyDayTask()

        assertEquals("Proyecto", mapped.boardName)
        assertEquals("b1", mapped.boardId)
        assertNull(mapped.addedAt)
    }

    private fun taskEntity(columnId: String?) = TaskEntity(
        id = "t1",
        columnId = columnId,
        title = "Tarea",
        description = null,
        priority = "MEDIUM",
        dueAt = null,
        assigneeId = "local-user",
        position = 0,
        createdAt = 0L,
        updatedAt = 0L,
        pending = true,
        deletedAt = null,
    )
}
