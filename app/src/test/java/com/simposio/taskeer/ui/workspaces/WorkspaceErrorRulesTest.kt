package com.simposio.taskeer.ui.workspaces

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceErrorRulesTest {

    private fun message(error: DomainError, retryAfter: Long? = null, action: WorkspaceAction = WorkspaceAction.CREATE) =
        WorkspaceErrorRules.messageFor(error, retryAfter, action)

    @Test
    fun limiteDelPlan_mensajeEspecifico() {
        assertEquals(WorkspaceErrorMessage.PlanLimit, message(DomainError.fromApi("WORKSPACE_LIMIT_REACHED", "x")))
    }

    @Test
    fun forbiddenGenerico_noEsElMensajeDeLimite() {
        assertEquals(WorkspaceErrorMessage.NoPermission, message(DomainError.fromApi("FORBIDDEN", "x")))
    }

    @Test
    fun forbiddenAlBorrar_pideSerPropietario() {
        assertEquals(
            WorkspaceErrorMessage.OnlyOwnerCanDelete,
            message(DomainError.fromApi("FORBIDDEN", "x"), action = WorkspaceAction.DELETE),
        )
    }

    @Test
    fun emailNoVerificado_pideVerificar() {
        assertEquals(WorkspaceErrorMessage.EmailNotVerified, message(DomainError.fromApi("EMAIL_NOT_VERIFIED", "x")))
    }

    @Test
    fun rateLimited_incluyeMinutos() {
        assertEquals(WorkspaceErrorMessage.RateLimited(3), message(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 125))
    }

    @Test
    fun rateLimited_sinRetryAfter_noTieneMinutos() {
        assertEquals(WorkspaceErrorMessage.RateLimited(null), message(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
    }

    @Test
    fun network_esSinConexion() {
        assertEquals(WorkspaceErrorMessage.Offline, message(DomainError.Network("NETWORK_ERROR", "x")))
    }

    @Test
    fun server_esServidorNoResponde() {
        assertEquals(WorkspaceErrorMessage.ServerDown, message(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))
    }

    @Test
    fun validation_pideRevisarElNombre() {
        assertEquals(WorkspaceErrorMessage.CheckName, message(DomainError.Validation("VALIDATION_ERROR", "x")))
    }

    @Test
    fun notFound_ya_noEstaDisponible() {
        assertEquals(WorkspaceErrorMessage.Unavailable, message(DomainError.NotFound("WORKSPACE_NOT_FOUND", "x")))
    }

    @Test
    fun unauthenticated_noTieneMensajePropio() {
        assertNull(message(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
    }

    @Test
    fun otro_esGenerico() {
        assertEquals(WorkspaceErrorMessage.Generic, message(DomainError.Unknown("X", "x")))
        assertEquals(WorkspaceErrorMessage.Generic, message(DomainError.Conflict("SLUG_ALREADY_TAKEN", "x")))
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() {
        val errors = listOf(
            DomainError.Forbidden("FORBIDDEN", "texto del servidor"),
            DomainError.Unknown("X", "texto del servidor"),
            DomainError.Server("INTERNAL_SERVER_ERROR", "texto del servidor"),
        )

        errors.forEach { assertEquals(false, message(it).toString().contains("texto del servidor")) }
    }
}
