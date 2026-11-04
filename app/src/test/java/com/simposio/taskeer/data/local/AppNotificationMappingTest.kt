package com.simposio.taskeer.data.local

import com.simposio.taskeer.domain.model.AppNotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNotificationMappingTest {

    private fun entity(kind: String = "DEADLINE") = AppNotificationEntity(
        id = "n1",
        kind = kind,
        taskId = "t1",
        taskTitle = "Tarea",
        activeTaskCount = null,
        delivered = false,
        createdAt = 1_000L,
        readAt = 2_000L,
        updatedAt = 3_000L,
        pending = true,
        deletedAt = null,
    )

    @Test
    fun entidad_aDominio_conservaTodosLosCampos() {
        val notification = requireNotNull(entity().toAppNotification())

        assertEquals(AppNotificationKind.DEADLINE, notification.kind)
        assertEquals("t1", notification.taskId)
        assertEquals(false, notification.delivered)
        assertEquals(1_000L, notification.createdAt.toEpochMilli())
        assertEquals(2_000L, notification.readAt?.toEpochMilli())
        assertEquals("n1", notification.id)
    }

    @Test
    fun ida_yVuelta_esIdentica() {
        val original = entity("REMINDER")

        assertEquals(original, original.toAppNotification()?.toEntity())
    }

    @Test
    fun tipoDesconocido_seOmite() {
        assertNull(entity("FUTURO").toAppNotification())
    }
}
