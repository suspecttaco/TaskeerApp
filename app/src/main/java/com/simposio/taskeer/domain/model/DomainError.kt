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

    data class RateLimited(override val code: String, override val message: String) : DomainError

    data class Server(override val code: String, override val message: String) : DomainError

    data class Unknown(override val code: String, override val message: String) : DomainError

    companion object {
        private const val SERVER_MESSAGE = "Error del servidor. Inténtalo de nuevo más tarde"

        private val VALIDATION_CODES = setOf(
            "VALIDATION_ERROR", "INVALID_RECOVERY_CODE", "INVALID_OR_EXPIRED_TOKEN",
            "INVALID_OR_EXPIRED_INVITATION", "INVALID_OAUTH_STATE", "INVALID_PROVIDER",
            "GOOGLE_EMAIL_MISSING", "NO_LOCAL_PASSWORD", "INVALID_COUNTRY", "INVALID_TIMEZONE",
            "CANNOT_CHANGE_OWNER_ROLE", "CANNOT_TRANSFER_TO_SELF",
            "OWNER_MUST_TRANSFER_OWNERSHIP", "CROSS_WORKSPACE_MOVE", "SAME_WORKSPACE",
            "REMINDER_NOT_PENDING", "ASSIGNEE_NOT_WORKSPACE_MEMBER", "USER_NOT_WORKSPACE_MEMBER",
        )
        private val UNAUTHENTICATED_CODES = setOf(
            "UNAUTHENTICATED", "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN",
            "INVALID_GOOGLE_TOKEN", "INVALID_STREAM_TICKET",
        )
        private val FORBIDDEN_CODES = setOf(
            "FORBIDDEN", "EMAIL_NOT_VERIFIED", "WORKSPACE_LIMIT_REACHED",
            "GOOGLE_EMAIL_NOT_VERIFIED", "UNTRUSTED_ORIGIN",
        )
        private val CONFLICT_CODES = setOf(
            "EMAIL_ALREADY_REGISTERED", "LOCAL_ACCOUNT_EMAIL_NOT_VERIFIED", "SLUG_ALREADY_TAKEN",
            "SLUG_CONFLICT", "COLUMN_NOT_EMPTY", "COLUMN_WIP_LIMIT_REACHED",
            "CONCURRENT_PASSWORD_CHANGE",
        )
        private val RATE_LIMITED_CODES = setOf("TOO_MANY_REQUESTS")
        // SUBSCRIPTION_NOT_FOUND termina en _NOT_FOUND pero el backend lo responde como 500.
        private val SERVER_CODES = setOf(
            "INTERNAL_SERVER_ERROR", "SUBSCRIPTION_NOT_FOUND", "ENTITLEMENT_NOT_CONFIGURED",
            "FREE_PLAN_NOT_SEEDED", "SLUG_GENERATION_FAILED", "EMAIL_VERIFICATION_DISABLED",
        )

        fun fromApi(code: String, message: String): DomainError = when {
            code in VALIDATION_CODES -> Validation(code, message)
            code in UNAUTHENTICATED_CODES -> Unauthenticated(code, message)
            code in FORBIDDEN_CODES -> Forbidden(code, message)
            code in CONFLICT_CODES -> Conflict(code, message)
            code in RATE_LIMITED_CODES -> RateLimited(code, message)
            code in SERVER_CODES -> Server(code, SERVER_MESSAGE)
            code.endsWith("_NOT_FOUND") -> NotFound(code, message)
            else -> Unknown(code, message)
        }
    }
}
