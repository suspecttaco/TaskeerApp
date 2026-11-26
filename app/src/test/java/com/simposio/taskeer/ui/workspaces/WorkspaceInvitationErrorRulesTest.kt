package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceInvitationErrorRulesTest {

    private fun message(error: DomainError, retry: Long? = null) = WorkspaceInvitationErrorRules.messageFor(error, retry)

    private fun api(code: String) = DomainError.fromApi(code, "mensaje del servidor")

    @Test
    fun emailNotVerified_mensajeEspecifico() {
        assertEquals(WorkspaceInvitationErrorMessage.EmailNotVerified, message(api("EMAIL_NOT_VERIFIED")))
    }

    @Test
    fun forbidden_noTienesPermiso() {
        assertEquals(WorkspaceInvitationErrorMessage.NoPermission, message(api("FORBIDDEN")))
    }

    @Test
    fun forbiddenOtroCodigo_esGenerico() {
        assertEquals(WorkspaceInvitationErrorMessage.Generic, message(DomainError.Forbidden("OTRO", "x")))
    }

    @Test
    fun invitacionInvalida_mensajeEspecifico() {
        assertEquals(WorkspaceInvitationErrorMessage.InvalidInvitation, message(api("INVALID_OR_EXPIRED_INVITATION")))
    }

    @Test
    fun notFound_invitacionYaNoValida() {
        assertEquals(WorkspaceInvitationErrorMessage.InvalidInvitation, message(api("INVITATION_NOT_FOUND")))
    }

    @Test
    fun workspaceDesaparecido_yaNoDisponible() {
        assertEquals(WorkspaceInvitationErrorMessage.WorkspaceGone, message(api("WORKSPACE_NOT_FOUND")))
    }

    @Test
    fun validationAlInvitar_revisaCorreoYRol() {
        assertEquals(WorkspaceInvitationErrorMessage.CheckEmailAndRole, message(api("VALIDATION_ERROR")))
    }

    @Test
    fun rateLimited_incluyeMinutos() {
        assertEquals(
            WorkspaceInvitationErrorMessage.RateLimited(2),
            message(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), retry = 120),
        )
    }

    @Test
    fun con429_sinRetryAfter_noTieneMinutos() {
        assertEquals(
            WorkspaceInvitationErrorMessage.RateLimited(null),
            message(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")),
        )
    }

    @Test
    fun sinRed_esOffline() {
        assertEquals(WorkspaceInvitationErrorMessage.Offline, message(DomainError.Network("NETWORK_ERROR", "x")))
    }

    @Test
    fun servidor_esServerDown() {
        assertEquals(WorkspaceInvitationErrorMessage.ServerDown, message(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))
    }

    @Test
    fun otro_esGenerico() {
        assertEquals(WorkspaceInvitationErrorMessage.Generic, message(DomainError.Conflict("DUPLICATE", "x")))
        assertEquals(WorkspaceInvitationErrorMessage.Generic, message(DomainError.Validation("OTRO", "x")))
    }

    @Test
    fun unauthenticated_noTieneMensajePropio() {
        assertNull(message(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() {
        val errors = listOf(api("EMAIL_NOT_VERIFIED"), api("FORBIDDEN"), api("INVALID_OR_EXPIRED_INVITATION"), api("DUPLICATE"))

        errors.forEach { assertEquals(false, message(it).toString().contains("mensaje del servidor")) }
    }
}
