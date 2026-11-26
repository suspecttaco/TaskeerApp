package com.simposio.taskeer.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Test

class AuthModelsTest {

    @Test
    fun toString_deSessionStateYAccountSummary_noIncluyeTokens() {
        val account = AccountSummary("ana@example.com", "Ana")
        val rendered = listOf(
            account.toString(),
            SessionState.SignedIn(account, offline = false).toString(),
            SessionState.Resuming.toString(),
            SessionState.SignedOut(expired = true).toString(),
            LoginOutcome.SignedIn(account).toString(),
            LogoutOutcome(serverRevoked = true).toString(),
        ).joinToString("\n").lowercase()

        assertFalse(rendered.contains("token"))
        assertFalse(rendered.contains("password"))
    }
}
