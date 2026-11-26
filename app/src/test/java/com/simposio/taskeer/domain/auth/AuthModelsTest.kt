package com.simposio.taskeer.domain.auth

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun toString_deRegisterOutcome_noIncluyeCodigosNiPassword() {
        val account = AccountSummary("ana@example.com", "Ana")
        val rendered = listOf(
            RegisterOutcome.Registered(account, RecoveryCodes(listOf("CTPQ-Q479", "ABCD-EFGH"))).toString(),
            RegisterOutcome.RegisteredWithoutCodes("ana@example.com").toString(),
            RegisterOutcome.Failed(DomainError.Network("X", "y")).toString(),
        ).joinToString("\n")

        assertFalse(rendered.contains("CTPQ"))
        assertFalse(rendered.contains("EFGH"))
        assertFalse(rendered.lowercase().contains("password"))
        assertTrue(rendered.contains("RecoveryCodes(count=2)"))
    }

    @Test
    fun toString_deOutcomesDeVerificacion_noIncluyeToken() {
        val error = DomainError.Validation("INVALID_OR_EXPIRED_TOKEN", "enlace vencido")
        val rendered = listOf(
            VerifyEmailOutcome.Verified.toString(),
            VerifyEmailOutcome.Failed(error, 60).toString(),
            ResendVerificationOutcome.Sent.toString(),
            ResendVerificationOutcome.AlreadyVerified.toString(),
        ).joinToString("\n")

        assertFalse(rendered.contains("secreto-abc"))
    }

    @Test
    fun accountSummary_sinEmailVerified_esNulo() {
        assertEquals(null, AccountSummary("ana@example.com", "Ana").emailVerified)
    }

    @Test
    fun toString_deOutcomesDeReset_noIncluyeSecretos() {
        val rendered = listOf(
            ResetPasswordOutcome.Reset.toString(),
            ResetPasswordOutcome.Failed(DomainError.Validation("INVALID_RECOVERY_CODE", "x")).toString(),
            ChangePasswordOutcome.Changed(AccountSummary("ana@example.com", "Ana")).toString(),
            ChangePasswordOutcome.ChangedSignedOut.toString(),
            ChangePasswordOutcome.Failed(DomainError.Network("X", "y")).toString(),
        ).joinToString("\n").lowercase()

        assertFalse(rendered.contains("newpassword"))
        assertFalse(rendered.contains("currentpassword"))
    }

    @Test
    fun toString_deRegenerated_noIncluyeLosCodigos() {
        val rendered = RegenerateCodesOutcome.Regenerated(RecoveryCodes(listOf("CTPQ-Q479", "ABCD-EFGH"))).toString()

        assertFalse(rendered.contains("CTPQ"))
        assertTrue(rendered.contains("RecoveryCodes(count=2)"))
    }

    @Test
    fun accountSummary_sinHasPassword_esNulo() {
        assertEquals(null, AccountSummary("ana@example.com", "Ana").hasPassword)
    }
}
