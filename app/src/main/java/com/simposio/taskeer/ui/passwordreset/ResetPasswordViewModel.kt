package com.simposio.taskeer.ui.passwordreset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.RecoveryCodeInput
import com.simposio.taskeer.domain.auth.ResetPasswordOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResetPasswordViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onFieldEdited() {
        _uiState.update { if (it.error == null) it else it.copy(error = null, retryMinutes = null) }
    }

    fun dismissNotSupported() {
        _uiState.update { if (it.block == ResetPasswordBlock.NOT_SUPPORTED) it.copy(block = null) else it }
    }

    fun submit(email: String, code: String, newPassword: CharArray, confirmation: CharArray) {
        if (!_uiState.value.canSubmit) {
            wipe(newPassword, confirmation)
            return
        }
        val normalizedCode = RecoveryCodeInput.normalize(code)
        val invalid = when {
            !AuthRules.isEmailShapeValid(email) -> ResetPasswordError.EMAIL_INVALID
            normalizedCode == null -> ResetPasswordError.CODE_INVALID
            else -> AuthRules.validateNewPassword(newPassword, confirmation)?.let(::resetPasswordValidationErrorFor)
        }
        confirmation.fill(ZERO)
        if (invalid != null || normalizedCode == null) {
            newPassword.fill(ZERO)
            _uiState.update { it.copy(error = invalid, retryMinutes = null, secretsWiped = it.secretsWiped + 1) }
            return
        }
        _uiState.update { it.copy(error = null, retryMinutes = null, isSubmitting = true) }
        viewModelScope.launch {
            try {
                apply(auth.resetPassword(AuthRules.normalizedEmail(email), normalizedCode, newPassword))
            } finally {
                newPassword.fill(ZERO)
                _uiState.update { it.copy(isSubmitting = false, secretsWiped = it.secretsWiped + 1) }
            }
        }
    }

    private fun apply(outcome: ResetPasswordOutcome) {
        when (outcome) {
            ResetPasswordOutcome.Reset -> _uiState.update { it.copy(done = true) }
            is ResetPasswordOutcome.Failed -> when (val failure = resetPasswordFailureFor(outcome.error)) {
                ResetPasswordFailure.NotSupported -> _uiState.update { it.copy(block = ResetPasswordBlock.NOT_SUPPORTED) }
                is ResetPasswordFailure.Inline -> applyInline(failure.error, outcome.retryAfterSeconds)
            }
        }
    }

    private fun applyInline(error: ResetPasswordError, retryAfterSeconds: Long?) {
        val rateLimited = error == ResetPasswordError.RATE_LIMITED
        val lockoutSeconds = if (rateLimited) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                error = error,
                retryMinutes = if (rateLimited) AuthRules.retryMinutes(retryAfterSeconds) else null,
                block = if (lockoutSeconds > 0) ResetPasswordBlock.RATE_LIMITED else it.block,
                incorrectCount = if (error == ResetPasswordError.INCORRECT) it.incorrectCount + 1 else it.incorrectCount,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { if (it.block == ResetPasswordBlock.RATE_LIMITED) it.copy(block = null) else it }
            }
        }
    }

    private fun wipe(newPassword: CharArray, confirmation: CharArray) {
        newPassword.fill(ZERO)
        confirmation.fill(ZERO)
    }

    private companion object {
        const val ZERO = '\u0000'
        const val MILLIS_PER_SECOND = 1_000L
    }
}
