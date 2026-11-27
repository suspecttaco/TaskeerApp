package com.simposio.taskeer.domain.sync

import com.simposio.taskeer.domain.task.UNDO_WINDOW
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionGraceRulesTest {

    private val deletedAt = Instant.parse("2026-11-26T18:30:00Z")

    @Test
    fun antesDeLaVentana_noElegible() {
        assertFalse(DeletionGraceRules.isEligible(deletedAt, deletedAt + UNDO_WINDOW.minusMillis(1)))
    }

    @Test
    fun alVencer_elegible() {
        assertTrue(DeletionGraceRules.isEligible(deletedAt, deletedAt + UNDO_WINDOW))
    }
}
