package com.simposio.taskeer.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScopeRulesTest {

    private val listTask = ScopeRow(taskInList = true)
    private val boardTask = ScopeRow(taskInBoard = true)

    @Test
    fun soloListaYTarea_sinSubtareasNiTableros() {
        val scope = SyncScope.effective(SyncScope.APP_LISTS_AND_TASKS, setOf(SyncEntity.LIST, SyncEntity.TASK))

        assertEquals(setOf(SyncEntity.LIST, SyncEntity.TASK), scope)
        assertTrue(SyncScope.allows(SyncEntity.LIST, ScopeRow(), scope))
        assertTrue(SyncScope.allows(SyncEntity.TASK, listTask, scope))
        assertFalse(SyncScope.allows(SyncEntity.SUBTASK, listTask, scope))
        assertFalse(SyncScope.allows(SyncEntity.BOARD, ScopeRow(), scope))
    }

    @Test
    fun tareaDeTablero_requiereBoardColumnTask() {
        val partial = setOf(SyncEntity.BOARD, SyncEntity.TASK)
        val full = setOf(SyncEntity.BOARD, SyncEntity.COLUMN, SyncEntity.TASK)

        assertFalse(SyncScope.allows(SyncEntity.TASK, boardTask, partial))
        assertTrue(SyncScope.allows(SyncEntity.TASK, boardTask, full))
    }

    @Test
    fun subtarea_requiereSuTarea() {
        val scope = setOf(SyncEntity.LIST, SyncEntity.SUBTASK)

        assertFalse(SyncScope.allows(SyncEntity.SUBTASK, listTask, scope))
        assertTrue(SyncScope.allows(SyncEntity.SUBTASK, listTask, scope + SyncEntity.TASK))
    }

    @Test
    fun appCrece_conLaFase() {
        val supported = SyncEntity.entries.toSet()
        val later = SyncScope.APP_LISTS_AND_TASKS + setOf(SyncEntity.BOARD, SyncEntity.COLUMN)

        assertEquals(SyncScope.APP_LISTS_AND_TASKS, SyncScope.effective(SyncScope.APP_LISTS_AND_TASKS, supported))
        assertEquals(later, SyncScope.effective(later, supported))
        assertEquals(emptySet<SyncEntity>(), SyncScope.effective(later, emptySet()))
    }
}
