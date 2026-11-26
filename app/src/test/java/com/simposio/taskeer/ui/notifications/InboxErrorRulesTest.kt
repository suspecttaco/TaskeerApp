package com.simposio.taskeer.ui.notifications

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InboxErrorRulesTest {

    @Test
    fun rateLimited_incluyeMinutos() {
        val message = InboxErrorRules.messageFor(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), 125)

        assertEquals(InboxErrorMessage.RateLimited(3), message)
    }

    @Test
    fun rateLimited_sinRetryAfter_noTieneMinutos() {
        assertEquals(
            InboxErrorMessage.RateLimited(null),
            InboxErrorRules.messageFor(DomainError.RateLimited("TOO_MANY_REQUESTS", "x"), null),
        )
    }

    @Test
    fun network_esSinConexion() {
        assertEquals(InboxErrorMessage.Offline, InboxErrorRules.messageFor(DomainError.Network("NETWORK_ERROR", "x"), null))
    }

    @Test
    fun server_esServidorNoResponde() {
        assertEquals(InboxErrorMessage.ServerDown, InboxErrorRules.messageFor(DomainError.Server("INTERNAL_SERVER_ERROR", "x"), null))
    }

    @Test
    fun otro_esGenerico() {
        assertEquals(InboxErrorMessage.Generic, InboxErrorRules.messageFor(DomainError.Unknown("X", "x"), null))
        assertEquals(InboxErrorMessage.Generic, InboxErrorRules.messageFor(DomainError.Forbidden("FORBIDDEN", "x"), null))
    }

    @Test
    fun sinSesion_noTieneMensajePropio() {
        assertNull(InboxErrorRules.messageFor(DomainError.Unauthenticated("UNAUTHENTICATED", "x"), null))
    }

    @Test
    fun nuncaUsaElMensajeDelServidor() {
        val secret = "mensaje-del-servidor"
        val errors = listOf(
            DomainError.Server("INTERNAL_SERVER_ERROR", secret),
            DomainError.Unknown("X", secret),
            DomainError.Network("N", secret),
            DomainError.RateLimited("TOO_MANY_REQUESTS", secret),
        )

        errors.forEach { assertFalse(InboxErrorRules.messageFor(it, 10).toString().contains(secret)) }
    }
}
