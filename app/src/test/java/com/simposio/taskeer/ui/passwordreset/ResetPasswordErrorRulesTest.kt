package com.simposio.taskeer.ui.passwordreset

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResetPasswordErrorRulesTest {

    private fun inline(error: DomainError) = (resetPasswordFailureFor(error) as ResetPasswordFailure.Inline).error

    @Test
    fun incorrecto_esElMismoParaCualquier400() {
        val codes = listOf("INVALID_RECOVERY_CODE", "VALIDATION_ERROR", "USER_NOT_FOUND", "RECOVERY_CODE_USED")

        val results = codes.map { inline(DomainError.Validation(it, "x")) }

        assertEquals(setOf(ResetPasswordError.INCORRECT), results.toSet())
    }

    @Test
    fun rateLimited_esRateLimited() {
        assertEquals(ResetPasswordError.RATE_LIMITED, inline(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
    }

    @Test
    fun network_esNetwork() {
        assertEquals(ResetPasswordError.NETWORK, inline(DomainError.Network("NETWORK_TIMEOUT", "x")))
    }

    @Test
    fun server_esServer() {
        assertEquals(ResetPasswordError.SERVER, inline(DomainError.Server("INTERNAL", "x")))
    }

    @Test
    fun mobileClientNotSupported_esNotSupported() {
        val failure = resetPasswordFailureFor(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))

        assertEquals(ResetPasswordFailure.NotSupported, failure)
    }

    @Test
    fun otro_esUnexpected() {
        assertEquals(ResetPasswordError.UNEXPECTED, inline(DomainError.Forbidden("FORBIDDEN", "x")))
        assertEquals(ResetPasswordError.UNEXPECTED, inline(DomainError.Unknown("HTTP_418", "x")))
        assertEquals(ResetPasswordError.UNEXPECTED, inline(DomainError.Conflict("CONFLICT", "x")))
    }

    @Test
    fun validacionLocal_sePintaPorCampo() {
        fun local(code: String) = resetPasswordValidationErrorFor(DomainError.Validation(code, "x"))

        assertEquals(ResetPasswordError.EMAIL_INVALID, local("EMAIL_INVALID"))
        assertEquals(ResetPasswordError.CODE_INVALID, local("CODE_INVALID"))
        assertEquals(ResetPasswordError.PASSWORD_INVALID, local("PASSWORD_TOO_SHORT"))
        assertEquals(ResetPasswordError.PASSWORD_MISMATCH, local("PASSWORD_MISMATCH"))
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val rendered = ResetPasswordError.entries.joinToString() +
            resetPasswordFailureFor(DomainError.Validation("INVALID_RECOVERY_CODE", "el correo no existe"))

        assertFalse(rendered.contains("no existe"))
    }
}
