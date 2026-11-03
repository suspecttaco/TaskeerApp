package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainErrorTest {

    @Test
    fun validacion_mapeaAValidation() {
        val error = DomainError.fromApi("VALIDATION_ERROR", "El título es obligatorio")
        assertEquals(DomainError.Validation("VALIDATION_ERROR", "El título es obligatorio"), error)
    }

    @Test
    fun notFound_mapeaANotFound() {
        val error = DomainError.fromApi("BOARD_NOT_FOUND", "No existe")
        assertEquals(DomainError.NotFound("BOARD_NOT_FOUND", "No existe"), error)
    }

    @Test
    fun noAutenticado_mapeaAUnauthenticated() {
        val error = DomainError.fromApi("UNAUTHENTICATED", "Sesión requerida")
        assertEquals(DomainError.Unauthenticated("UNAUTHENTICATED", "Sesión requerida"), error)
    }

    @Test
    fun limiteDePlan_mapeaAForbidden() {
        val error = DomainError.fromApi("WORKSPACE_LIMIT_REACHED", "Límite del plan")
        assertEquals(DomainError.Forbidden("WORKSPACE_LIMIT_REACHED", "Límite del plan"), error)
    }

    @Test
    fun serverError_mapeaAServerConMensajeGenerico() {
        val error = DomainError.fromApi("INTERNAL_SERVER_ERROR", "Fallo interno")

        assertEquals(
            DomainError.Server("INTERNAL_SERVER_ERROR", "Error del servidor. Inténtalo de nuevo más tarde"),
            error,
        )
    }

    @Test
    fun tablaDeCodigos_cadaCodigoResuelveSuVarianteYConservaElCode() {
        val table: Map<(String, String) -> DomainError, List<String>> = mapOf(
            DomainError::Validation to listOf(
                "VALIDATION_ERROR", "INVALID_RECOVERY_CODE", "INVALID_OR_EXPIRED_TOKEN",
                "INVALID_OR_EXPIRED_INVITATION", "INVALID_OAUTH_STATE", "INVALID_PROVIDER",
                "GOOGLE_EMAIL_MISSING", "NO_LOCAL_PASSWORD", "INVALID_COUNTRY", "INVALID_TIMEZONE",
                "CANNOT_CHANGE_OWNER_ROLE", "CANNOT_TRANSFER_TO_SELF", "OWNER_MUST_TRANSFER_OWNERSHIP",
                "CROSS_WORKSPACE_MOVE", "SAME_WORKSPACE", "REMINDER_NOT_PENDING",
                "ASSIGNEE_NOT_WORKSPACE_MEMBER", "USER_NOT_WORKSPACE_MEMBER",
            ),
            DomainError::Unauthenticated to listOf(
                "UNAUTHENTICATED", "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN",
                "INVALID_GOOGLE_TOKEN", "INVALID_STREAM_TICKET",
            ),
            DomainError::Forbidden to listOf(
                "FORBIDDEN", "EMAIL_NOT_VERIFIED", "WORKSPACE_LIMIT_REACHED",
                "GOOGLE_EMAIL_NOT_VERIFIED", "UNTRUSTED_ORIGIN",
            ),
            DomainError::NotFound to listOf(
                "WORKSPACE_NOT_FOUND", "BOARD_NOT_FOUND", "COLUMN_NOT_FOUND", "TASK_NOT_FOUND",
                "SUBTASK_NOT_FOUND", "REMINDER_NOT_FOUND", "NOTIFICATION_NOT_FOUND",
                "MEMBER_NOT_FOUND", "INVITATION_NOT_FOUND",
            ),
            DomainError::Conflict to listOf(
                "EMAIL_ALREADY_REGISTERED", "LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED", "SLUG_ALREADY_TAKEN",
                "SLUG_CONFLICT", "COLUMN_NOT_EMPTY", "COLUMN_WIP_LIMIT_REACHED",
                "CONCURRENT_PASSWORD_CHANGE",
            ),
            DomainError::RateLimited to listOf("TOO_MANY_REQUESTS"),
        )

        table.forEach { (variant, codes) ->
            codes.forEach { code ->
                val actual = DomainError.fromApi(code, "mensaje")
                assertEquals(code, variant(code, "mensaje"), actual)
                assertEquals(code, actual.code)
            }
        }
    }

    @Test
    fun codigosDe5xx_resuelvenAServerConservandoElCode() {
        listOf(
            "INTERNAL_SERVER_ERROR", "SUBSCRIPTION_NOT_FOUND", "ENTITLEMENT_NOT_CONFIGURED",
            "FREE_PLAN_NOT_SEEDED", "SLUG_GENERATION_FAILED", "EMAIL_VERIFICATION_DISABLED",
        ).forEach { code ->
            val actual = DomainError.fromApi(code, "detalle interno")

            assertTrue(code, actual is DomainError.Server)
            assertEquals(code, actual.code)
            assertEquals("Error del servidor. Inténtalo de nuevo más tarde", actual.message)
        }
    }

    @Test
    fun excepcionesAlSufijo_seResuelvenPorCodigoExacto() {
        assertTrue(DomainError.fromApi("COLUMN_WIP_LIMIT_REACHED", "m") is DomainError.Conflict)
        assertTrue(DomainError.fromApi("SUBSCRIPTION_NOT_FOUND", "m") is DomainError.Server)
        assertTrue(DomainError.fromApi("INVALID_CREDENTIALS", "m") is DomainError.Unauthenticated)
        assertTrue(DomainError.fromApi("SLUG_GENERATION_FAILED", "m") is DomainError.Server)
        assertTrue(DomainError.fromApi("SLUG_CONFLICT", "m") is DomainError.Conflict)
    }

    @Test
    fun limiteDesconocido_siguesiendoUnknown() {
        assertTrue(DomainError.fromApi("ALGO_LIMIT_REACHED", "m") is DomainError.Unknown)
    }

    @Test
    fun notFoundDesconocido_resuelveANotFoundPorSufijo() {
        assertTrue(DomainError.fromApi("ALGO_NOT_FOUND", "m") is DomainError.NotFound)
    }

    @Test
    fun codigoEnMinusculasOVacio_esUnknown() {
        assertTrue(DomainError.fromApi("validation_error", "m") is DomainError.Unknown)
        assertTrue(DomainError.fromApi("", "m") is DomainError.Unknown)
    }

    @Test
    fun codigoDesconocido_conservaCodeYMessage() {
        val error = DomainError.fromApi("RAREZA_NUEVA", "algo pasó")
        assertTrue(error is DomainError.Unknown)
        assertEquals("RAREZA_NUEVA", error.code)
        assertEquals("algo pasó", error.message)
    }

    @Test
    fun resultadoSuccessYFailure() {
        val success = DomainResult.Success(7)
        assertEquals(7, success.value)
        assertTrue(success is DomainResult.Success)
        assertTrue(DomainResult.Failure(DomainError.Network("SIN_RED", "sin conexión")) is DomainResult.Failure)
    }
}
