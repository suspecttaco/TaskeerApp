package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChangePasswordErrorRulesTest {

    @Test
    fun invalidCredentials_vaAlCampoActual() {
        val error = changePasswordErrorFor(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x"))

        assertEquals(ChangePasswordError.CURRENT_INCORRECT, error)
    }

    @Test
    fun unauthenticated_pideIniciarSesion() {
        assertEquals(ChangePasswordError.NEEDS_SIGN_IN, changePasswordErrorFor(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
        assertEquals(ChangePasswordError.NEEDS_SIGN_IN, changePasswordErrorFor(DomainError.Unauthenticated("NOT_SIGNED_IN", "x")))
    }

    @Test
    fun noLocalPassword_esSuPropioError() {
        assertEquals(ChangePasswordError.NO_LOCAL_PASSWORD, changePasswordErrorFor(DomainError.Validation("NO_LOCAL_PASSWORD", "x")))
    }

    @Test
    fun concurrentPasswordChange_esConcurrentChange() {
        assertEquals(
            ChangePasswordError.CONCURRENT_CHANGE,
            changePasswordErrorFor(DomainError.Conflict("CONCURRENT_PASSWORD_CHANGE", "x")),
        )
    }

    @Test
    fun otro400_esCheckData() {
        assertEquals(ChangePasswordError.CHECK_DATA, changePasswordErrorFor(DomainError.Validation("VALIDATION_ERROR", "x")))
    }

    @Test
    fun validacionLocal_sePintaPorCampo() {
        assertEquals(ChangePasswordError.CURRENT_INVALID, changePasswordErrorFor(DomainError.Validation("PASSWORD_REQUIRED", "x")))
        assertEquals(ChangePasswordError.NEW_INVALID, changePasswordErrorFor(DomainError.Validation("PASSWORD_TOO_SHORT", "x")))
        assertEquals(ChangePasswordError.MISMATCH, changePasswordErrorFor(DomainError.Validation("PASSWORD_MISMATCH", "x")))
    }

    @Test
    fun network_esNetwork() {
        assertEquals(ChangePasswordError.NETWORK, changePasswordErrorFor(DomainError.Network("NETWORK_TIMEOUT", "x")))
    }

    @Test
    fun rateLimited_esRateLimited() {
        assertEquals(ChangePasswordError.RATE_LIMITED, changePasswordErrorFor(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
    }

    @Test
    fun server_yOtro() {
        assertEquals(ChangePasswordError.SERVER, changePasswordErrorFor(DomainError.Server("INTERNAL", "x")))
        assertEquals(ChangePasswordError.UNEXPECTED, changePasswordErrorFor(DomainError.Forbidden("FORBIDDEN", "x")))
        assertEquals(ChangePasswordError.UNEXPECTED, changePasswordErrorFor(DomainError.Conflict("CONFLICT", "x")))
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val rendered = changePasswordErrorFor(DomainError.Validation("VALIDATION_ERROR", "contraseña previa repetida")).toString()

        assertFalse(rendered.contains("repetida"))
    }
}
