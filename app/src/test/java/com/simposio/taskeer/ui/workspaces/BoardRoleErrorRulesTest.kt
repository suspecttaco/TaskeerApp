package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BoardRoleErrorRulesTest {

    private fun message(error: DomainError, retryAfter: Long? = null) = BoardRoleErrorRules.messageFor(error, retryAfter)

    private fun api(code: String) = DomainError.fromApi(code, "texto del servidor")

    @Test
    fun forbidden_noTienesPermiso() {
        assertEquals(BoardRoleErrorMessage.ForbiddenAction, message(api("FORBIDDEN")))
    }

    @Test
    fun tableroDesaparecido_yaNoDisponible() {
        assertEquals(BoardRoleErrorMessage.BoardGone, message(api("BOARD_NOT_FOUND")))
        assertEquals(BoardRoleErrorMessage.BoardGone, message(api("WORKSPACE_NOT_FOUND")))
    }

    @Test
    fun otroNotFound_esGenerico() {
        assertEquals(BoardRoleErrorMessage.Generic, message(api("MEMBER_NOT_FOUND")))
    }

    @Test
    fun noEsMiembro_mensajeEspecifico() {
        assertEquals(BoardRoleErrorMessage.NotAMember, message(api("USER_NOT_WORKSPACE_MEMBER")))
    }

    @Test
    fun validacionConOtroCodigo_esGenerica() {
        assertEquals(BoardRoleErrorMessage.Generic, message(api("VALIDATION_ERROR")))
    }

    @Test
    fun rateLimited_incluyeMinutos() {
        assertEquals(BoardRoleErrorMessage.RateLimited(3), message(api("TOO_MANY_REQUESTS"), 125))
        assertEquals(BoardRoleErrorMessage.RateLimited(null), message(api("TOO_MANY_REQUESTS")))
    }

    @Test
    fun network_esSinConexion() {
        assertEquals(BoardRoleErrorMessage.Offline, message(DomainError.Network("NETWORK_ERROR", "x")))
    }

    @Test
    fun server_esErrorDelServidor() {
        assertEquals(BoardRoleErrorMessage.ServerDown, message(api("INTERNAL_SERVER_ERROR")))
    }

    @Test
    fun otro_esGenerico() {
        assertEquals(BoardRoleErrorMessage.Generic, message(DomainError.Unknown("RARO", "x")))
        assertEquals(BoardRoleErrorMessage.Generic, message(DomainError.Conflict("SLUG_CONFLICT", "x")))
    }

    @Test
    fun sesionCaida_noTieneMensajePropio() {
        assertNull(message(api("UNAUTHENTICATED")))
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() {
        val secret = "SECRETO-DEL-SERVIDOR"
        val errors = listOf(
            DomainError.Forbidden("FORBIDDEN", secret),
            DomainError.NotFound("BOARD_NOT_FOUND", secret),
            DomainError.Validation("USER_NOT_WORKSPACE_MEMBER", secret),
            DomainError.Server("INTERNAL_SERVER_ERROR", secret),
            DomainError.Unknown("RARO", secret),
        )

        errors.forEach { assertFalse(message(it).toString().contains(secret)) }
    }
}
