package com.simposio.taskeer.ui.verifyemail

enum class VerifyEmailPhase { INPUT, VERIFYING, VERIFIED }

enum class VerifyEmailError {
    INPUT_INVALID,
    INVALID_OR_EXPIRED,
    DISABLED,
    NEEDS_SIGN_IN,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    NOT_SUPPORTED,
    UNEXPECTED,
}

enum class VerifyEmailBlock { RATE_LIMITED }

// Sin token, enlace ni texto pegado: viven solo en el campo de la pantalla y en la petición.
data class VerifyEmailUiState(
    val phase: VerifyEmailPhase = VerifyEmailPhase.INPUT,
    val error: VerifyEmailError? = null,
    val retryMinutes: Int? = null,
    val blocked: VerifyEmailBlock? = null,
) {
    val isSubmitting: Boolean
        get() = phase == VerifyEmailPhase.VERIFYING

    val canSubmit: Boolean
        get() = phase == VerifyEmailPhase.INPUT && blocked != VerifyEmailBlock.RATE_LIMITED
}
