package com.simposio.taskeer.data.remote

import com.simposio.taskeer.domain.model.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMapperTest {

    private fun map(status: Int, body: String, headers: Map<String, String> = emptyMap()) =
        ApiErrorMapper.map(status, headers, body)

    @Test
    fun unauthenticated401_mapeaAUnauthenticated() {
        val failure = map(401, errorBody("UNAUTHENTICATED", "Sin token"))

        assertEquals(DomainError.Unauthenticated("UNAUTHENTICATED", "Sin token"), failure.error)
    }

    @Test
    fun forbidden403_mapeaAForbidden() {
        assertEquals(DomainError.Forbidden("FORBIDDEN", "No"), map(403, errorBody("FORBIDDEN", "No")).error)
    }

    @Test
    fun taskNotFound404_mapeaANotFound() {
        assertEquals(
            DomainError.NotFound("TASK_NOT_FOUND", "No existe"),
            map(404, errorBody("TASK_NOT_FOUND", "No existe")).error,
        )
    }

    @Test
    fun validationError400_mapeaAValidation() {
        assertEquals(
            DomainError.Validation("VALIDATION_ERROR", "Título requerido"),
            map(400, errorBody("VALIDATION_ERROR", "Título requerido")).error,
        )
    }

    @Test
    fun columnWipLimit409_mapeaAConflict() {
        assertEquals(
            DomainError.Conflict("COLUMN_WIP_LIMIT_REACHED", "Límite"),
            map(409, errorBody("COLUMN_WIP_LIMIT_REACHED", "Límite")).error,
        )
    }

    @Test
    fun tooManyRequests429_mapeaARateLimited() {
        val error = map(429, errorBody("TOO_MANY_REQUESTS", "Espera")).error

        assertEquals(DomainError.RateLimited("TOO_MANY_REQUESTS", "Espera"), error)
    }

    @Test
    fun internalServerError500_mapeaAServerConMensajeGenerico() {
        val error = map(500, errorBody("INTERNAL_SERVER_ERROR", "stack trace interno")).error

        assertEquals(
            DomainError.Server("INTERNAL_SERVER_ERROR", "Error del servidor. Inténtalo de nuevo más tarde"),
            error,
        )
    }

    @Test
    fun codigoDesconocido409_conservaElCodigoYEsConflict() {
        val error = map(409, errorBody("ID_ALREADY_IN_USE", "Id en uso")).error

        assertEquals(DomainError.Conflict("ID_ALREADY_IN_USE", "Id en uso"), error)
    }

    @Test
    fun codigoDesconocido503_conservaElCodigoYEsServer() {
        val error = map(503, errorBody("MAINTENANCE", "interno")).error

        assertTrue(error is DomainError.Server)
        assertEquals("MAINTENANCE", error.code)
    }

    @Test
    fun cuerpoTextoPlano429_esRateLimited() {
        val error = map(429, "Too many requests, please try again later.").error

        assertEquals("TOO_MANY_REQUESTS", error.code)
        assertTrue(error is DomainError.RateLimited)
    }

    @Test
    fun cuerpoVacio404_esNotFoundGenerico() {
        assertEquals("NOT_FOUND", map(404, "").error.code)
        assertTrue(map(404, "").error is DomainError.NotFound)
    }

    @Test
    fun cuerpoJsonSinError_clasificaPorEstado() {
        val error = map(409, "{\"ok\":false}").error

        assertEquals(DomainError.Conflict("CONFLICT", "La operación entra en conflicto con el estado actual"), error)
    }

    @Test
    fun estado418_sinSobre_esUnknownHttp418() {
        val error = map(418, "").error

        assertTrue(error is DomainError.Unknown)
        assertEquals("HTTP_418", error.code)
    }

    @Test
    fun retryAfterEntero_seLee() {
        assertEquals(30L, map(429, "", mapOf("retry-after" to "30")).retryAfterSeconds)
    }

    @Test
    fun sinRetryAfterUsaRateLimitReset() {
        assertEquals(12L, map(429, "", mapOf("ratelimit-reset" to "12")).retryAfterSeconds)
    }

    @Test
    fun retryAfterFechaHttp_esNull() {
        val headers = mapOf("retry-after" to "Wed, 21 Oct 2026 07:28:00 GMT")

        assertNull(map(503, "", headers).retryAfterSeconds)
    }

    @Test
    fun untrustedOrigin403_mapeaAForbiddenMobileClientNotSupported() {
        val error = map(403, errorBody("UNTRUSTED_ORIGIN", "Origin no permitido")).error

        assertTrue(error is DomainError.Forbidden)
        assertEquals("MOBILE_CLIENT_NOT_SUPPORTED", error.code)
    }

    @Test
    fun untrustedOrigin403_mensajeEnEspanolSinDatosDelServidor() {
        val error = map(403, errorBody("UNTRUSTED_ORIGIN", "Origin http://evil.example no permitido")).error

        assertEquals(
            "El servidor no admite todavía esta versión de la app. Actualiza la aplicación o inténtalo más tarde.",
            error.message,
        )
        assertFalse(error.message.contains("evil"))
    }

    @Test
    fun forbiddenGenerico403_sigueSiendoForbidden() {
        assertEquals("FORBIDDEN", map(403, errorBody("FORBIDDEN")).error.code)
    }

    @Test
    fun untrustedOriginConOtroEstado_noSeMapeaAMobileClientNotSupported() {
        val error = map(401, errorBody("UNTRUSTED_ORIGIN")).error

        assertFalse(error.code == "MOBILE_CLIENT_NOT_SUPPORTED")
    }

    @Test
    fun todosLosCodigosDelCatalogo_devuelvenElTipoDeFromApi() {
        val statusByCode = listOf(
            400 to "VALIDATION_ERROR", 400 to "INVALID_RECOVERY_CODE", 400 to "INVALID_COUNTRY",
            400 to "REMINDER_NOT_PENDING", 401 to "UNAUTHENTICATED", 401 to "INVALID_CREDENTIALS",
            401 to "INVALID_REFRESH_TOKEN", 401 to "INVALID_GOOGLE_TOKEN", 403 to "FORBIDDEN",
            403 to "EMAIL_NOT_VERIFIED", 403 to "WORKSPACE_LIMIT_REACHED", 404 to "WORKSPACE_NOT_FOUND",
            404 to "BOARD_NOT_FOUND", 404 to "COLUMN_NOT_FOUND", 404 to "TASK_NOT_FOUND",
            409 to "EMAIL_ALREADY_REGISTERED", 409 to "SLUG_ALREADY_TAKEN", 409 to "COLUMN_NOT_EMPTY",
            409 to "COLUMN_WIP_LIMIT_REACHED", 422 to "ASSIGNEE_NOT_WORKSPACE_MEMBER",
            429 to "TOO_MANY_REQUESTS", 500 to "INTERNAL_SERVER_ERROR", 500 to "SUBSCRIPTION_NOT_FOUND",
            500 to "FREE_PLAN_NOT_SEEDED", 503 to "EMAIL_VERIFICATION_DISABLED",
        )

        statusByCode.forEach { (status, code) ->
            val expected = DomainError.fromApi(code, "m")
            val actual = map(status, errorBody(code, "m")).error
            assertEquals("código $code", expected::class, actual::class)
        }
    }
}
