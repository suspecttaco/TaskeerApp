package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.auth.GoogleCredential
import com.simposio.taskeer.domain.auth.GoogleCredentialResult
import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleSignInErrorRulesTest {

    private fun inline(error: DomainError) = (googleFailureFor(error) as LoginFailure.Inline).error

    @Test
    fun cancelado_noMuestraError() {
        assertNull(googleResultErrorFor(GoogleCredentialResult.Cancelled))
    }

    @Test
    fun obtenido_noMuestraError() {
        assertNull(googleResultErrorFor(GoogleCredentialResult.Obtained(GoogleCredential("t", "n"))))
    }

    @Test
    fun sinCuenta_muestraElAvisoDeAnadirUna() {
        assertEquals(LoginError.GOOGLE_NO_ACCOUNT, googleResultErrorFor(GoogleCredentialResult.NoAccount))
    }

    @Test
    fun noDisponible_muestraGoogleNoDisponible() {
        assertEquals(LoginError.GOOGLE_UNAVAILABLE, googleResultErrorFor(GoogleCredentialResult.Unavailable))
    }

    @Test
    fun falloDelSelector_muestraNoSePudoObtenerLaCuenta() {
        assertEquals(LoginError.GOOGLE_FAILED, googleResultErrorFor(GoogleCredentialResult.Failed))
    }

    @Test
    fun tokenInvalido_muestraNoSePudoVerificar() {
        assertEquals(LoginError.GOOGLE_TOKEN_INVALID, inline(DomainError.Unauthenticated("INVALID_GOOGLE_TOKEN", "x")))
    }

    @Test
    fun otro401_esInesperado() {
        assertEquals(LoginError.GOOGLE_UNEXPECTED, inline(DomainError.Unauthenticated("UNAUTHENTICATED", "x")))
    }

    @Test
    fun correoNoVerificadoEnGoogle_muestraSuMensaje() {
        assertEquals(LoginError.GOOGLE_EMAIL_NOT_VERIFIED, inline(DomainError.Forbidden("GOOGLE_EMAIL_NOT_VERIFIED", "x")))
    }

    @Test
    fun cuentaLocalSinVerificar_muestraSuMensaje() {
        assertEquals(
            LoginError.LOCAL_ACCOUNT_NOT_VERIFIED,
            inline(DomainError.Conflict("LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED", "x")),
        )
    }

    @Test
    fun cualquier400_muestraNoSePudoCompletarElAcceso() {
        listOf("INVALID_PROVIDER", "GOOGLE_EMAIL_MISSING", "INVALID_OAUTH_STATE", "VALIDATION_ERROR").forEach {
            assertEquals(it, LoginError.GOOGLE_REJECTED, inline(DomainError.Validation(it, "x")))
        }
    }

    @Test
    fun cuentaSinContrasenaLocal_muestraSuMensaje() {
        assertEquals(LoginError.NO_LOCAL_PASSWORD, inline(DomainError.Validation("NO_LOCAL_PASSWORD", "x")))
    }

    @Test
    fun canjeRechazado_muestraNoSePudoCompletarElAcceso() {
        assertEquals(LoginError.GOOGLE_REJECTED, inline(DomainError.Validation("GOOGLE_EXCHANGE_REJECTED", "x")))
    }

    @Test
    fun canjeNoDisponible_esServer() {
        assertEquals(LoginError.SERVER, inline(DomainError.Server("GOOGLE_EXCHANGE_UNAVAILABLE", "x")))
    }

    @Test
    fun rateLimited_esRateLimited() {
        assertEquals(LoginError.RATE_LIMITED, inline(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
    }

    @Test
    fun red_esNetwork() {
        assertEquals(LoginError.NETWORK, inline(DomainError.Network("NETWORK_TIMEOUT", "x")))
    }

    @Test
    fun servidor_esServer() {
        assertEquals(LoginError.SERVER, inline(DomainError.Server("INTERNAL_SERVER_ERROR", "x")))
    }

    @Test
    fun versionNoCompatible_esLaPaginaDeEstado() {
        assertEquals(
            LoginFailure.NotSupported,
            googleFailureFor(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x")),
        )
    }

    @Test
    fun otro_esInesperado() {
        listOf(
            DomainError.NotFound("ROUTE_NOT_FOUND", "x"),
            DomainError.Unknown("RARO", "x"),
            DomainError.Forbidden("FORBIDDEN", "x"),
            DomainError.Conflict("OTRO", "x"),
            DomainError.Validation("ALREADY_SIGNED_IN", "x"),
        ).forEach { assertEquals(it.toString(), LoginError.GOOGLE_UNEXPECTED, inline(it)) }
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val source = com.simposio.taskeer.testing.MainSources.kotlin("ui/auth/GoogleSignInErrorRules.kt")

        val code = source.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")

        assertEquals(false, code.contains(".message"))
    }
}
