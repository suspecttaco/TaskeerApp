package com.simposio.taskeer.ui.passwordreset

enum class ResetPasswordError {
    EMAIL_INVALID,
    CODE_INVALID,
    PASSWORD_INVALID,
    PASSWORD_MISMATCH,
    INCORRECT,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    UNEXPECTED,
}

enum class ResetPasswordBlock { NOT_SUPPORTED, RATE_LIMITED }

// Sin contraseñas ni código de recuperación: viven solo en los campos de la pantalla y en la petición.
// `secretsWiped` y `incorrectCount` solo avisan a la pantalla de que vacíe los campos.
data class ResetPasswordUiState(
    val isSubmitting: Boolean = false,
    val error: ResetPasswordError? = null,
    val retryMinutes: Int? = null,
    val block: ResetPasswordBlock? = null,
    val done: Boolean = false,
    val secretsWiped: Int = 0,
    val incorrectCount: Int = 0,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && !done && block != ResetPasswordBlock.RATE_LIMITED
}
