package com.simposio.taskeer.domain.model

sealed interface DomainError {
    val code: String
    val message: String

    data class Validation(override val code: String, override val message: String) : DomainError

    data class NotFound(override val code: String, override val message: String) : DomainError

    data class Unauthenticated(
        override val code: String,
        override val message: String,
    ) : DomainError

    data class Forbidden(override val code: String, override val message: String) : DomainError

    data class Conflict(override val code: String, override val message: String) : DomainError

    data class Network(override val code: String, override val message: String) : DomainError

    data class Unknown(override val code: String, override val message: String) : DomainError

    companion object {
        fun fromApi(code: String, message: String): DomainError = when {
            code == "VALIDATION_ERROR" -> Validation(code, message)
            code.endsWith("_NOT_FOUND") -> NotFound(code, message)
            code == "UNAUTHENTICATED" -> Unauthenticated(code, message)
            // El backend responde 403 con *_LIMIT_REACHED cuando se supera un límite del plan.
            code.endsWith("_LIMIT_REACHED") -> Forbidden(code, message)
            else -> Unknown(code, message)
        }
    }
}
