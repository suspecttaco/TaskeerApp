package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegisterErrorRulesTest {

    private fun inline(error: DomainError) =
        (registerFailureFor(error) as RegisterFailure.Inline).error

    @Test
    fun validacionLocal_mapeaCadaCodigo() {
        val expected = mapOf(
            "DISPLAY_NAME_INVALID" to RegisterError.NAME_INVALID,
            "EMAIL_INVALID" to RegisterError.EMAIL_INVALID,
            "PASSWORD_TOO_SHORT" to RegisterError.PASSWORD_TOO_SHORT,
            "PASSWORD_TOO_LONG" to RegisterError.PASSWORD_TOO_LONG,
            "PASSWORD_MISMATCH" to RegisterError.PASSWORD_MISMATCH,
        )

        expected.forEach { (code, error) -> assertEquals(code, error, inline(DomainError.Validation(code, "x"))) }
    }

    @Test
    fun conflictEmailAlreadyRegistered_esEmailTaken() {
        assertEquals(RegisterError.EMAIL_TAKEN, inline(DomainError.Conflict("EMAIL_ALREADY_REGISTERED", "x")))
    }

    @Test
    fun conflictOtro_esUnexpected() {
        assertEquals(RegisterError.UNEXPECTED, inline(DomainError.Conflict("OTRO", "x")))
    }

    @Test
    fun rateLimited_esRateLimited() {
        assertEquals(RegisterError.RATE_LIMITED, inline(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
    }

    @Test
    fun network_esNetwork() {
        assertEquals(RegisterError.NETWORK, inline(DomainError.Network("NETWORK_TIMEOUT", "x")))
    }

    @Test
    fun server_esServer() {
        assertEquals(RegisterError.SERVER, inline(DomainError.Server("X", "x")))
    }

    @Test
    fun validationDelServidor_esValidation() {
        assertEquals(RegisterError.VALIDATION, inline(DomainError.Validation("VALIDATION_ERROR", "x")))
    }

    @Test
    fun mobileClientNotSupported_esNotSupported() {
        assertEquals(
            RegisterFailure.NotSupported,
            registerFailureFor(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x")),
        )
    }

    @Test
    fun otrosForbidden_sonUnexpected() {
        assertEquals(RegisterError.UNEXPECTED, inline(DomainError.Forbidden("FORBIDDEN", "x")))
        assertEquals(RegisterError.UNEXPECTED, inline(DomainError.NotFound("X", "x")))
        assertEquals(RegisterError.UNEXPECTED, inline(DomainError.Unauthenticated("X", "x")))
        assertEquals(RegisterError.UNEXPECTED, inline(DomainError.Unknown("X", "x")))
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val failure = registerFailureFor(DomainError.Conflict("EMAIL_ALREADY_REGISTERED", "ana@example.com ya existe"))

        assertFalse(failure.toString().contains("ana@example.com"))
    }
}
