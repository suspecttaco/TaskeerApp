package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.task.UNDO_SYNC_MARGIN
import com.simposio.taskeer.domain.task.UNDO_WINDOW
import com.simposio.taskeer.domain.task.deletionSyncReleaseAt
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionUndoWindowTest {

    @Test
    fun undoWindow_esTresSegundos() {
        assertEquals(Duration.ofSeconds(3), UNDO_WINDOW)
    }

    @Test
    fun deletionSyncReleaseAt_sumaVentanaYMargen() {
        val deletedAt = Instant.parse("2026-11-18T10:00:00Z")

        assertEquals(Instant.parse("2026-11-18T10:00:08Z"), deletionSyncReleaseAt(deletedAt))
    }

    @Test
    fun elMargenEsPositivo() {
        assertTrue(UNDO_SYNC_MARGIN > Duration.ZERO)
    }
}
