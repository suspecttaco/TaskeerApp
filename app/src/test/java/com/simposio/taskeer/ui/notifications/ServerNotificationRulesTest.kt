package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.ServerNotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerNotificationRulesTest {

    @Test
    fun etiqueta_recordatorio() = assertEquals(R.string.inbox_type_reminder, ServerNotificationRules.labelRes(ServerNotificationType.REMINDER))

    @Test
    fun etiqueta_tareaAsignada() =
        assertEquals(R.string.inbox_type_task_assigned, ServerNotificationRules.labelRes(ServerNotificationType.TASK_ASSIGNED))

    @Test
    fun etiqueta_cambioDeRol() =
        assertEquals(R.string.inbox_type_role_changed, ServerNotificationRules.labelRes(ServerNotificationType.ROLE_CHANGED))

    @Test
    fun etiqueta_seguridad() =
        assertEquals(R.string.inbox_type_security, ServerNotificationRules.labelRes(ServerNotificationType.RECOVERY_CODE_USED))

    @Test
    fun etiqueta_invitacion() = assertEquals(R.string.inbox_type_invitation, ServerNotificationRules.labelRes(ServerNotificationType.INVITATION))

    @Test
    fun etiqueta_mencion() = assertEquals(R.string.inbox_type_mention, ServerNotificationRules.labelRes(ServerNotificationType.MENTION))

    @Test
    fun etiqueta_desconocido_esAviso() = assertEquals(R.string.inbox_type_unknown, ServerNotificationRules.labelRes(ServerNotificationType.UNKNOWN))

    @Test
    fun icono_cadaTipoTieneElSuyo() {
        val icons = ServerNotificationType.entries.associateWith { ServerNotificationRules.icon(it) }

        assertEquals(ServerNotificationType.entries.size, icons.values.toSet().size)
        assertEquals(InboxIcon.GENERIC, icons.getValue(ServerNotificationType.UNKNOWN))
    }

    @Test
    fun seguridad_sedestaca() {
        assertTrue(ServerNotificationRules.highlightsSecurity(ServerNotificationType.RECOVERY_CODE_USED))
        assertFalse(ServerNotificationRules.highlightsSecurity(ServerNotificationType.TASK_ASSIGNED))
    }
}
