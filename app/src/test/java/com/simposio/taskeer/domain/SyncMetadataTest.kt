package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.afterLocalChange
import com.simposio.taskeer.domain.model.afterLocalDelete
import com.simposio.taskeer.domain.model.newClientId
import com.simposio.taskeer.domain.model.newSyncMetadata
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMetadataTest {

    private val now = Instant.parse("2026-10-14T10:15:30Z")

    @Test
    fun newSyncMetadata_generaIdUuidYPendiente() {
        val metadata = newSyncMetadata(now)

        assertEquals(metadata.clientId, UUID.fromString(metadata.clientId).toString())
        assertEquals(now, metadata.updatedAt)
        assertTrue(metadata.pending)
        assertNull(metadata.deletedAt)
    }

    @Test
    fun newSyncMetadata_idsDistintosPorLlamada() {
        assertNotEquals(newClientId(), newClientId())
    }

    @Test
    fun afterLocalChange_actualizaUpdatedAtYMarcaPendiente() {
        val synced = newSyncMetadata(now).copy(pending = false)
        val later = now.plusSeconds(60)

        val changed = synced.afterLocalChange(later)

        assertEquals(later, changed.updatedAt)
        assertTrue(changed.pending)
        assertEquals(synced.clientId, changed.clientId)
    }

    @Test
    fun afterLocalDelete_marcaDeletedAtPendingYUpdatedAt() {
        val later = now.plusSeconds(120)

        val deleted = newSyncMetadata(now).afterLocalDelete(later)

        assertEquals(later, deleted.deletedAt)
        assertEquals(later, deleted.updatedAt)
        assertTrue(deleted.pending)
        assertTrue(deleted.isDeleted)
    }

    @Test
    fun isDeleted_falsoConDeletedAtNulo() {
        assertFalse(newSyncMetadata(now).isDeleted)
    }

    @Test
    fun isDeleted_verdaderoTrasBorrado() {
        assertTrue(newSyncMetadata(now).afterLocalDelete(now).isDeleted)
    }
}
