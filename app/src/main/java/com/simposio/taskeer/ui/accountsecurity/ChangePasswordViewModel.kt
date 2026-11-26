package com.simposio.taskeer.ui.accountsecurity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.ChangePasswordOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChangePasswordViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onFieldEdited() {
        _uiState.update { if (it.error == null) it else it.copy(error = null, retryMinutes = null) }
    }

    fun submit(current: CharArray, newPassword: CharArray, confirmation: CharArray) {
        if (!_uiState.value.canSubmit) {
            wipe(current, newPassword, confirmation)
            return
        }
        val invalid = AuthRules.validateChangePassword(current, newPassword, confirmation)
        confirmation.fill(ZERO)
        if (invalid != null) {
            current.fill(ZERO)
            newPassword.fill(ZERO)
            _uiState.update {
                it.copy(error = changePasswordErrorFor(invalid), retryMinutes = null, secretsWiped = it.secretsWiped + 1)
            }
            return
        }
        _uiState.update { it.copy(error = null, retryMinutes = null, isSubmitting = true) }
        viewModelScope.launch {
            try {
                apply(auth.changePassword(current, newPassword))
            } finally {
                current.fill(ZERO)
                newPassword.fill(ZERO)
                _uiState.update { it.copy(isSubmitting = false, secretsWiped = it.secretsWiped + 1) }
            }
        }
    }

    private fun apply(outcome: ChangePasswordOutcome) {
        when (outcome) {
            is ChangePasswordOutcome.Changed -> _uiState.update { it.copy(done = ChangePasswordDone.CHANGED) }
            ChangePasswordOutcome.ChangedSignedOut -> _uiState.update { it.copy(done = ChangePasswordDone.SIGNED_OUT) }
            is ChangePasswordOutcome.Failed -> applyFailure(changePasswordErrorFor(outcome.error), outcome.retryAfterSeconds)
        }
    }

    private fun applyFailure(error: ChangePasswordError, retryAfterSeconds: Long?) {
        val rateLimited = error == ChangePasswordError.RATE_LIMITED
        val lockoutSeconds = if (rateLimited) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                error = error,
                retryMinutes = if (rateLimited) AuthRules.retryMinutes(retryAfterSeconds) else null,
                blocked = lockoutSeconds > 0 || it.blocked,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { it.copy(blocked = false) }
            }
        }
    }

    private fun wipe(current: CharArray, newPassword: CharArray, confirmation: CharArray) {
        current.fill(ZERO)
        newPassword.fill(ZERO)
        confirmation.fill(ZERO)
    }

    private companion object {
        const val ZERO = '\u0000'
        const val MILLIS_PER_SECOND = 1_000L
    }
}
