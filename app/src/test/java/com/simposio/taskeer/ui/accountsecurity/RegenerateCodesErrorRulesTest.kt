package com.simposio.taskeer.ui.accountsecurity

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Test

class RegenerateCodesErrorRulesTest {

    @Test
    fun invalidCredentials_esCurrentIncorrect() {
        assertEquals(
            RegenerateCodesError.CURRENT_INCORRECT,
            regenerateCodesErrorFor(DomainError.Unauthenticated("INVALID_CREDENTIALS", "x")),
        )
    }

    @Test
    fun otro401_pideIniciarSesion() {
        assertEquals(
            RegenerateCodesError.NEEDS_SIGN_IN,
            regenerateCodesErrorFor(DomainError.Unauthenticated("NOT_SIGNED_IN", "x")),
        )
    }

    @Test
    fun noLocalPassword_esSuPropioError() {
        assertEquals(RegenerateCodesError.NO_LOCAL_PASSWORD, regenerateCodesErrorFor(DomainError.Validation("NO_LOCAL_PASSWORD", "x")))
    }

    @Test
    fun contrasenaVacia_esCurrentInvalid() {
        assertEquals(RegenerateCodesError.CURRENT_INVALID, regenerateCodesErrorFor(DomainError.Validation("PASSWORD_REQUIRED", "x")))
    }

    @Test
    fun otro400_esCheckData() {
        assertEquals(RegenerateCodesError.CHECK_DATA, regenerateCodesErrorFor(DomainError.Validation("VALIDATION_ERROR", "x")))
    }

    @Test
    fun rateLimitedRedServerYOtro() {
        assertEquals(RegenerateCodesError.RATE_LIMITED, regenerateCodesErrorFor(DomainError.RateLimited("T", "x")))
        assertEquals(RegenerateCodesError.NETWORK, regenerateCodesErrorFor(DomainError.Network("N", "x")))
        assertEquals(RegenerateCodesError.SERVER, regenerateCodesErrorFor(DomainError.Server("S", "x")))
        assertEquals(RegenerateCodesError.UNEXPECTED, regenerateCodesErrorFor(DomainError.Forbidden("F", "x")))
    }
}
