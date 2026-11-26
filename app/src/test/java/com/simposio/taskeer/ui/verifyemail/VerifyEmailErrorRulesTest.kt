package com.simposio.taskeer.ui.verifyemail

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Test

class VerifyEmailErrorRulesTest {

    private val verify = VerifyEmailOperation.VERIFY
    private val resend = VerifyEmailOperation.RESEND

    @Test
    fun invalidOrExpired_alVerificar() {
        val error = DomainError.Validation("INVALID_OR_EXPIRED_TOKEN", "x")

        assertEquals(VerifyEmailError.INVALID_OR_EXPIRED, verifyEmailErrorFor(error, verify))
    }

    @Test
    fun invalidOrExpired_cualquierValidation400SeTrataIgual() {
        val error = DomainError.Validation("VALIDATION_ERROR", "x")

        assertEquals(VerifyEmailError.INVALID_OR_EXPIRED, verifyEmailErrorFor(error, verify))
    }

    @Test
    fun tokenLocalInvalido_esInputInvalid() {
        val error = DomainError.Validation("TOKEN_INVALID", "x")

        assertEquals(VerifyEmailError.INPUT_INVALID, verifyEmailErrorFor(error, verify))
    }

    @Test
    fun suspension503_esDisabled() {
        val error = DomainError.Server("EMAIL_VERIFICATION_DISABLED", "x")

        assertEquals(VerifyEmailError.DISABLED, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun unauthenticated_alReenviarPideIniciarSesion() {
        val error = DomainError.Unauthenticated("NOT_SIGNED_IN", "x")

        assertEquals(VerifyEmailError.NEEDS_SIGN_IN, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun unauthenticated_alVerificarEsUnexpected() {
        val error = DomainError.Unauthenticated("UNAUTHENTICATED", "x")

        assertEquals(VerifyEmailError.UNEXPECTED, verifyEmailErrorFor(error, verify))
    }

    @Test
    fun rateLimited_enAmbasOperaciones() {
        val error = DomainError.RateLimited("TOO_MANY_REQUESTS", "x")

        assertEquals(VerifyEmailError.RATE_LIMITED, verifyEmailErrorFor(error, verify))
        assertEquals(VerifyEmailError.RATE_LIMITED, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun network_enAmbasOperaciones() {
        val error = DomainError.Network("NETWORK_TIMEOUT", "x")

        assertEquals(VerifyEmailError.NETWORK, verifyEmailErrorFor(error, verify))
        assertEquals(VerifyEmailError.NETWORK, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun server_genericoEsServer() {
        val error = DomainError.Server("INTERNAL_SERVER_ERROR", "x")

        assertEquals(VerifyEmailError.SERVER, verifyEmailErrorFor(error, verify))
        assertEquals(VerifyEmailError.SERVER, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun mobileClientNotSupported_alReenviarEsNotSupported() {
        val error = DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x")

        assertEquals(VerifyEmailError.NOT_SUPPORTED, verifyEmailErrorFor(error, resend))
    }

    @Test
    fun otro_esUnexpected() {
        assertEquals(VerifyEmailError.UNEXPECTED, verifyEmailErrorFor(DomainError.Unknown("X", "x"), verify))
        assertEquals(VerifyEmailError.UNEXPECTED, verifyEmailErrorFor(DomainError.Forbidden("FORBIDDEN", "x"), resend))
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val error = DomainError.Validation("INVALID_OR_EXPIRED_TOKEN", "MENSAJE-DEL-SERVIDOR")

        assertEquals(false, verifyEmailErrorFor(error, verify).name.contains("MENSAJE"))
    }
}
