package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.task.restoredPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPlacementRulesTest {

    @Test
    fun restoredPosition_originalLibre() {
        assertEquals(1, restoredPosition(original = 1, occupied = setOf(0, 2), activeCount = 2))
    }

    @Test
    fun restoredPosition_ocupada_vaAlFinal() {
        assertEquals(3, restoredPosition(original = 1, occupied = setOf(0, 1, 2), activeCount = 3))
    }
}
