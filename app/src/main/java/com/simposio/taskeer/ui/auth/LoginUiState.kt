package com.simposio.taskeer.ui.auth

enum class LoginError {
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_LONG,
    INVALID_CREDENTIALS,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    VALIDATION,
    UNEXPECTED,
}

enum class LoginBlock { NOT_SUPPORTED, RATE_LIMITED }

enum class LoginNotice { ACCOUNT_CREATED, PASSWORD_RESET }

data class LoginHandoff(val email: String, val notice: LoginNotice)

// Sin contraseña ni tokens: la contraseña vive solo en el campo de la pantalla y en un CharArray efímero.
data class LoginUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
    val retryMinutes: Int? = null,
    val blocked: LoginBlock? = null,
    val passwordResetCount: Int = 0,
    val signedIn: Boolean = false,
    val notice: LoginNotice? = null,
) {
    val canSubmit: Boolean get() = !isSubmitting && blocked != LoginBlock.RATE_LIMITED
}
