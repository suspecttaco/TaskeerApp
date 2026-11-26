package com.simposio.taskeer.ui.auth

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.testing.MainSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorRulesTest {

    private fun inline(error: DomainError) = (loginFailureFor(error) as LoginFailure.Inline).error

    @Test
    fun invalidCredentials_mapeaAMensajeGenerico_sinDistinguirCorreo() {
        val wrongPassword = DomainError.Unauthenticated("INVALID_CREDENTIALS", "Contraseña incorrecta")
        val unknownEmail = DomainError.Unauthenticated("INVALID_CREDENTIALS", "El correo no existe")

        assertEquals(LoginError.INVALID_CREDENTIALS, inline(wrongPassword))
        assertEquals(inline(wrongPassword), inline(unknownEmail))
    }

    @Test
    fun rateLimited_conRetryAfter_mapeaAMinutos() {
        assertEquals(LoginError.RATE_LIMITED, inline(DomainError.RateLimited("TOO_MANY_REQUESTS", "x")))
        assertTrue(MainSources.strings().contains("""name="auth_error_rate_limited_minutes">Demasiados intentos. Inténtalo de nuevo en %1${'$'}d min."""))
    }

    @Test
    fun rateLimited_sinDato_mapeaAMensajeGenerico() {
        assertTrue(MainSources.strings().contains("""name="auth_error_rate_limited">Demasiados intentos. Inténtalo de nuevo en unos minutos"""))
    }

    @Test
    fun network_mapeaAMensajeDeRed() {
        assertEquals(LoginError.NETWORK, inline(DomainError.Network("NETWORK_TIMEOUT", "x")))
    }

    @Test
    fun server_mapeaAMensajeDeServidor() {
        assertEquals(LoginError.SERVER, inline(DomainError.Server("INVALID_LOGIN_RESPONSE", "x")))
    }

    @Test
    fun mobileClientNotSupported_mapeaABloqueoNotSupported() {
        val failure = loginFailureFor(DomainError.Forbidden("MOBILE_CLIENT_NOT_SUPPORTED", "x"))

        assertEquals(LoginFailure.NotSupported, failure)
    }

    @Test
    fun validation400_mapeaARevisaTusDatos() {
        assertEquals(LoginError.VALIDATION, inline(DomainError.Validation("VALIDATION_ERROR", "x")))
        assertEquals(LoginError.EMAIL_INVALID, inline(DomainError.Validation("EMAIL_INVALID", "x")))
        assertEquals(LoginError.PASSWORD_REQUIRED, inline(DomainError.Validation("PASSWORD_REQUIRED", "x")))
        assertEquals(LoginError.PASSWORD_TOO_LONG, inline(DomainError.Validation("PASSWORD_TOO_LONG", "x")))
    }

    @Test
    fun unknown_mapeaAMensajeGenerico() {
        assertEquals(LoginError.UNEXPECTED, inline(DomainError.Unknown("HTTP_418", "x")))
        assertEquals(LoginError.UNEXPECTED, inline(DomainError.Conflict("CONFLICT", "x")))
        assertEquals(LoginError.UNEXPECTED, inline(DomainError.Forbidden("EMAIL_NOT_VERIFIED", "x")))
        assertEquals(LoginError.UNEXPECTED, inline(DomainError.Validation("ALREADY_SIGNED_IN", "x")))
    }

    @Test
    fun nuncaExponeElMensajeDelServidor() {
        val secret = "mensaje-interno-del-servidor"
        val errors = listOf(
            DomainError.Unauthenticated("INVALID_CREDENTIALS", secret),
            DomainError.Server("INTERNAL_SERVER_ERROR", secret),
            DomainError.Unknown("X", secret),
        )

        errors.forEach { assertTrue(loginFailureFor(it) is LoginFailure.Inline) }
        assertTrue(MainSources.kotlin("ui/auth/LoginScreen.kt").let { !it.contains(".message") })
    }

    @Test
    fun mensajeDeNoCompatible_coincideConElTextoFijoDelCliente() {
        val strings = MainSources.strings()
        val remote = MainSources.kotlin("data/remote/ApiErrorMapper.kt")
        val clientText = Regex("MOBILE_CLIENT_MESSAGE =\\s*\"(.*?)\"").find(remote)!!.groupValues[1]

        assertTrue(strings.contains("name=\"auth_not_supported_message\">$clientText</string>"))
    }
}
