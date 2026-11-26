package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceMemberErrorRulesTest {

    private fun message(error: DomainError, retryAfter: Long? = null) = WorkspaceMemberErrorRules.messageFor(error, retryAfter)

    private fun api(code: String) = DomainError.fromApi(code, "texto del servidor")

    @Test
    fun ownerRole_mensajeEspecifico() {
        assertEquals(WorkspaceMemberErrorMessage.OwnerRoleImmutable, message(api("CANNOT_CHANGE_OWNER_ROLE")))
    }

    @Test
    fun ownerMustTransfer_mensajeEspecifico() {
        assertEquals(WorkspaceMemberErrorMessage.OwnerMustTransfer, message(api("OWNER_MUST_TRANSFER_OWNERSHIP")))
    }

    @Test
    fun transferASiMismo_mensajeEspecifico() {
        assertEquals(WorkspaceMemberErrorMessage.TransferToSelf, message(api("CANNOT_TRANSFER_TO_SELF")))
    }

    @Test
    fun noEsMiembro_mensajeEspecifico() {
        assertEquals(WorkspaceMemberErrorMessage.NotAMember, message(api("USER_NOT_WORKSPACE_MEMBER")))
    }

    @Test
    fun forbidden_noTienesPermiso() {
        assertEquals(WorkspaceMemberErrorMessage.ForbiddenAction, message(api("FORBIDDEN")))
    }

    @Test
    fun miembroDesaparecido_yaNoEsMiembro() {
        assertEquals(WorkspaceMemberErrorMessage.MemberGone, message(api("MEMBER_NOT_FOUND")))
    }

    @Test
    fun workspaceDesaparecido_yaNoDisponible() {
        assertEquals(WorkspaceMemberErrorMessage.WorkspaceGone, message(api("WORKSPACE_NOT_FOUND")))
    }

    @Test
    fun rateLimited_incluyeMinutos() {
        assertEquals(WorkspaceMemberErrorMessage.RateLimited(3), message(api("TOO_MANY_REQUESTS"), 125))
        assertEquals(WorkspaceMemberErrorMessage.RateLimited(null), message(api("TOO_MANY_REQUESTS")))
    }

    @Test
    fun network_esSinConexion() {
        assertEquals(WorkspaceMemberErrorMessage.Offline, message(DomainError.Network("NETWORK_ERROR", "x")))
    }

    @Test
    fun server_esErrorDelServidor() {
        assertEquals(WorkspaceMemberErrorMessage.ServerDown, message(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))
    }

    @Test
    fun unauthenticated_noTieneMensajePropio() {
        assertNull(message(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
    }

    @Test
    fun otro_esGenerico() {
        assertEquals(WorkspaceMemberErrorMessage.Generic, message(DomainError.Unknown("RARO", "x")))
        assertEquals(WorkspaceMemberErrorMessage.Generic, message(api("VALIDATION_ERROR")))
        assertEquals(WorkspaceMemberErrorMessage.Generic, message(DomainError.NotFound("BOARD_NOT_FOUND", "x")))
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() {
        val errors = listOf(
            api("FORBIDDEN"),
            api("CANNOT_CHANGE_OWNER_ROLE"),
            api("MEMBER_NOT_FOUND"),
            api("INTERNAL_SERVER_ERROR"),
            DomainError.Unknown("RARO", "texto del servidor"),
        )

        assertEquals(emptyList<Any>(), errors.mapNotNull { message(it) }.filter { it.toString().contains("servidor") })
    }
}
