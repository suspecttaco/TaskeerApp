package com.simposio.taskeer.ui.components

import androidx.annotation.StringRes
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DomainError

enum class ErrorPageKind { NOT_FOUND, NO_PERMISSION, UNEXPECTED, SERVICE_UNAVAILABLE, SESSION_EXPIRED, OFFLINE }

data class ErrorPageSpec(
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val actionRes: Int,
)

fun errorPageSpecFor(kind: ErrorPageKind): ErrorPageSpec = when (kind) {
    ErrorPageKind.NOT_FOUND -> ErrorPageSpec(
        R.string.error_not_found_title,
        R.string.error_not_found_message,
        R.string.error_action_home,
    )
    ErrorPageKind.NO_PERMISSION -> ErrorPageSpec(
        R.string.error_no_permission_title,
        R.string.error_no_permission_message,
        R.string.error_action_home,
    )
    ErrorPageKind.UNEXPECTED -> ErrorPageSpec(
        R.string.error_unexpected_title,
        R.string.error_unexpected_message,
        R.string.error_action_retry,
    )
    ErrorPageKind.SERVICE_UNAVAILABLE -> ErrorPageSpec(
        R.string.error_service_unavailable_title,
        R.string.error_service_unavailable_message,
        R.string.error_action_retry,
    )
    ErrorPageKind.SESSION_EXPIRED -> ErrorPageSpec(
        R.string.error_session_expired_title,
        R.string.error_session_expired_message,
        R.string.error_action_sign_in,
    )
    ErrorPageKind.OFFLINE -> ErrorPageSpec(
        R.string.error_offline_title,
        R.string.error_offline_message,
        R.string.error_action_retry,
    )
}

// Validation, Conflict y RateLimited son errores de acción: siguen en línea en su pantalla.
fun errorPageKindFor(error: DomainError): ErrorPageKind? = when (error) {
    is DomainError.NotFound -> ErrorPageKind.NOT_FOUND
    is DomainError.Forbidden -> ErrorPageKind.NO_PERMISSION
    is DomainError.Unauthenticated -> ErrorPageKind.SESSION_EXPIRED
    is DomainError.Server -> ErrorPageKind.SERVICE_UNAVAILABLE
    is DomainError.Network -> ErrorPageKind.OFFLINE
    is DomainError.Unknown -> ErrorPageKind.UNEXPECTED
    is DomainError.Validation, is DomainError.Conflict, is DomainError.RateLimited -> null
}
