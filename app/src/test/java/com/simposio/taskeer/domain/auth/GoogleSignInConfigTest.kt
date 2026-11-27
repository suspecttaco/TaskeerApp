package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInConfigTest {

    @Test
    fun completa_disponible() {
        assertTrue(GoogleSignInConfig("web-client", exchangeConfigured = true).isAvailable)
    }

    @Test
    fun sinCanjeConfigurado_noDisponible() {
        assertFalse(GoogleSignInConfig("web-client", exchangeConfigured = false).isAvailable)
    }

    @Test
    fun sinWebClientId_noDisponible() {
        assertFalse(GoogleSignInConfig("", exchangeConfigured = true).isAvailable)
        assertFalse(GoogleSignInConfig("  ", exchangeConfigured = true).isAvailable)
    }
}
