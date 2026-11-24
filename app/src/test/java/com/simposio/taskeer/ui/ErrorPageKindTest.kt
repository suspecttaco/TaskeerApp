package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.ui.components.ErrorPageKind
import com.simposio.taskeer.ui.components.errorPageKindFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorPageKindTest {

    @Test
    fun notFound_aNotFound() {
        assertEquals(ErrorPageKind.NOT_FOUND, errorPageKindFor(DomainError.NotFound("c", "m")))
    }

    @Test
    fun forbidden_aNoPermission() {
        assertEquals(ErrorPageKind.NO_PERMISSION, errorPageKindFor(DomainError.Forbidden("c", "m")))
    }

    @Test
    fun unauthenticated_aSessionExpired() {
        assertEquals(ErrorPageKind.SESSION_EXPIRED, errorPageKindFor(DomainError.Unauthenticated("c", "m")))
    }

    @Test
    fun server_aServiceUnavailable() {
        assertEquals(ErrorPageKind.SERVICE_UNAVAILABLE, errorPageKindFor(DomainError.Server("c", "m")))
    }

    @Test
    fun network_aOffline() {
        assertEquals(ErrorPageKind.OFFLINE, errorPageKindFor(DomainError.Network("c", "m")))
    }

    @Test
    fun unknown_aUnexpected() {
        assertEquals(ErrorPageKind.UNEXPECTED, errorPageKindFor(DomainError.Unknown("c", "m")))
    }

    @Test
    fun validationConflictYRateLimited_noSonPagina() {
        assertNull(errorPageKindFor(DomainError.Validation("c", "m")))
        assertNull(errorPageKindFor(DomainError.Conflict("c", "m")))
        assertNull(errorPageKindFor(DomainError.RateLimited("c", "m")))
    }
}
