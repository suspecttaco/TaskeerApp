package com.simposio.taskeer.ui.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegisterOutcome
import com.simposio.taskeer.domain.model.DomainError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegisterUiState(
            phase = if (savedState.get<Boolean>(KEY_CODES_PENDING) == true) {
                RegisterPhase.CODES_LOST
            } else {
                RegisterPhase.FORM
            },
        ),
    )
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // Único lugar donde viven los códigos: memoria, nunca en UiState ni en SavedStateHandle.
    private val _recoveryCodes = MutableStateFlow<RecoveryCodes?>(null)
    val recoveryCodes: StateFlow<RecoveryCodes?> = _recoveryCodes.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, error = null, retryMinutes = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null, retryMinutes = null) }
    }

    fun onPasswordEdited() {
        _uiState.update { if (it.error == null) it else it.copy(error = null, retryMinutes = null) }
    }

    fun dismissNotSupported() {
        _uiState.update { if (it.blocked == RegisterBlock.NOT_SUPPORTED) it.copy(blocked = null) else it }
    }

    fun submit(displayName: String, email: String, password: CharArray, confirmation: CharArray) {
        if (!_uiState.value.canSubmit) {
            wipe(password, confirmation)
            return
        }
        val invalid = AuthRules.validateRegistration(displayName, email, password)
        if (invalid != null) {
            wipe(password, confirmation)
            showValidationError(displayName, email, invalid)
            return
        }
        if (!AuthRules.passwordsMatch(password, confirmation)) {
            wipe(password, confirmation)
            val mismatch = DomainError.Validation(AuthRules.PASSWORD_MISMATCH, "Las contraseñas no coinciden")
            showValidationError(displayName, email, mismatch)
            return
        }
        confirmation.fill(ZERO)
        _uiState.update {
            it.copy(displayName = displayName, email = email, error = null, retryMinutes = null, isSubmitting = true)
        }
        viewModelScope.launch {
            try {
                apply(auth.register(displayName, email, password), email)
            } finally {
                password.fill(ZERO)
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onSavedCheckedChange(checked: Boolean) {
        _uiState.update { if (it.phase == RegisterPhase.CODES) it.copy(savedChecked = checked) else it }
    }

    fun onCodesCopied() {
        _uiState.update { if (it.phase == RegisterPhase.CODES) it.copy(copied = true) else it }
    }

    fun confirmCodesSaved() {
        val current = _uiState.value
        if (current.phase == RegisterPhase.CODES && current.savedChecked) discardCodesAndFinish()
    }

    fun leaveWithoutSaving() {
        if (_uiState.value.phase == RegisterPhase.CODES) discardCodesAndFinish()
    }

    fun acknowledgeLostOrMissingCodes() {
        val phase = _uiState.value.phase
        if (phase != RegisterPhase.CODES_LOST && phase != RegisterPhase.NO_CODES) return
        savedState.remove<Boolean>(KEY_CODES_PENDING)
        _uiState.update { it.copy(phase = RegisterPhase.FINISHED) }
    }

    public override fun onCleared() {
        _recoveryCodes.value = null
    }

    private fun showValidationError(displayName: String, email: String, invalid: DomainError.Validation) {
        val failure = registerFailureFor(invalid) as RegisterFailure.Inline
        _uiState.update {
            it.copy(displayName = displayName, email = email, error = failure.error, retryMinutes = null)
        }
    }

    private fun discardCodesAndFinish() {
        // Se retira la fase antes que los códigos: `CODES` siempre implica códigos presentes.
        _uiState.update { it.copy(phase = RegisterPhase.FINISHED) }
        _recoveryCodes.value = null
        savedState.remove<Boolean>(KEY_CODES_PENDING)
    }

    private fun apply(outcome: RegisterOutcome, email: String) {
        when (outcome) {
            is RegisterOutcome.Registered -> {
                _recoveryCodes.value = outcome.recoveryCodes
                savedState[KEY_CODES_PENDING] = true
                _uiState.update {
                    it.copy(
                        phase = RegisterPhase.CODES,
                        passwordResetCount = it.passwordResetCount + 1,
                        finishedEmail = email.trim(),
                    )
                }
            }
            is RegisterOutcome.RegisteredWithoutCodes ->
                _uiState.update {
                    it.copy(
                        phase = RegisterPhase.NO_CODES,
                        passwordResetCount = it.passwordResetCount + 1,
                        finishedEmail = outcome.email,
                    )
                }
            is RegisterOutcome.Failed -> when (val failure = registerFailureFor(outcome.error)) {
                RegisterFailure.NotSupported -> _uiState.update { it.copy(blocked = RegisterBlock.NOT_SUPPORTED) }
                is RegisterFailure.Inline -> applyInline(failure.error, outcome.retryAfterSeconds)
            }
        }
    }

    private fun applyInline(error: RegisterError, retryAfterSeconds: Long?) {
        val rateLimited = error == RegisterError.RATE_LIMITED
        val lockoutSeconds = if (rateLimited) AuthRules.lockoutSeconds(retryAfterSeconds) else 0L
        _uiState.update {
            it.copy(
                error = error,
                retryMinutes = if (rateLimited) AuthRules.retryMinutes(retryAfterSeconds) else null,
                blocked = if (lockoutSeconds > 0) RegisterBlock.RATE_LIMITED else it.blocked,
            )
        }
        if (lockoutSeconds > 0) {
            viewModelScope.launch {
                delay(lockoutSeconds * MILLIS_PER_SECOND)
                _uiState.update { if (it.blocked == RegisterBlock.RATE_LIMITED) it.copy(blocked = null) else it }
            }
        }
    }

    private fun wipe(password: CharArray, confirmation: CharArray) {
        password.fill(ZERO)
        confirmation.fill(ZERO)
    }

    companion object {
        const val KEY_CODES_PENDING = "codes_pending"
        private const val ZERO = '\u0000'
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
