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
    fun serverError_mapeaAUnknown() {
        val error = DomainError.fromApi("INTERNAL_SERVER_ERROR", "Fallo interno")
        assertEquals(DomainError.Unknown("INTERNAL_SERVER_ERROR", "Fallo interno"), error)
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
