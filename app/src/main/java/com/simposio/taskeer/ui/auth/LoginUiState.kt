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
    NO_LOCAL_PASSWORD,
    GOOGLE_NO_ACCOUNT,
    GOOGLE_UNAVAILABLE,
    GOOGLE_FAILED,
    GOOGLE_TOKEN_INVALID,
    GOOGLE_EMAIL_NOT_VERIFIED,
    LOCAL_ACCOUNT_NOT_VERIFIED,
    GOOGLE_REJECTED,
    GOOGLE_UNEXPECTED,
}

enum class LoginPhase { FORM, CODES, CODES_LOST }

enum class LoginBlock { NOT_SUPPORTED, RATE_LIMITED }

enum class LoginNotice { ACCOUNT_CREATED, PASSWORD_RESET }

data class LoginHandoff(val email: String, val notice: LoginNotice)

// Sin contraseña, tokens ni códigos: la contraseña vive solo en el campo de la pantalla y en un CharArray efímero,
// y los códigos de una cuenta nueva solo en `LoginViewModel.recoveryCodes`.
data class LoginUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
    val retryMinutes: Int? = null,
    val blocked: LoginBlock? = null,
    val passwordResetCount: Int = 0,
    val signedIn: Boolean = false,
    val notice: LoginNotice? = null,
    val phase: LoginPhase = LoginPhase.FORM,
    val googleAvailable: Boolean = false,
    val googleInProgress: Boolean = false,
    val savedChecked: Boolean = false,
    val copied: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting && !googleInProgress && blocked != LoginBlock.RATE_LIMITED

    val canUseGoogle: Boolean get() = googleAvailable && canSubmit

    val canContinue: Boolean get() = phase == LoginPhase.CODES && savedChecked
}
