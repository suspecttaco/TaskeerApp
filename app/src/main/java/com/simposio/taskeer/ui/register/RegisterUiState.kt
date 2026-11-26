package com.simposio.taskeer.ui.register

enum class RegisterPhase { FORM, CODES, CODES_LOST, NO_CODES, FINISHED }

enum class RegisterError {
    NAME_INVALID,
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_MISMATCH,
    EMAIL_TAKEN,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    VALIDATION,
    UNEXPECTED,
}

enum class RegisterBlock { NOT_SUPPORTED, RATE_LIMITED }

// Sin códigos de recuperación, contraseñas ni tokens: los códigos viven solo en `RegisterViewModel.recoveryCodes`.
data class RegisterUiState(
    val phase: RegisterPhase = RegisterPhase.FORM,
    val displayName: String = "",
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: RegisterError? = null,
    val retryMinutes: Int? = null,
    val blocked: RegisterBlock? = null,
    val passwordResetCount: Int = 0,
    val savedChecked: Boolean = false,
    val copied: Boolean = false,
    val finishedEmail: String? = null,
) {
    val canSubmit: Boolean
        get() = phase == RegisterPhase.FORM && !isSubmitting && blocked != RegisterBlock.RATE_LIMITED

    val canContinue: Boolean
        get() = phase == RegisterPhase.CODES && savedChecked
}
