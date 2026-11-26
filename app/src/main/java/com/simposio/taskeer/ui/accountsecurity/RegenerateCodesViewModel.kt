package com.simposio.taskeer.ui.accountsecurity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.AuthRepository
import com.simposio.taskeer.domain.auth.AuthRules
import com.simposio.taskeer.domain.auth.RecoveryCodes
import com.simposio.taskeer.domain.auth.RegenerateCodesOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegenerateCodesViewModel(
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegenerateCodesUiState(
            phase = if (savedState.get<Boolean>(KEY_CODES_PENDING) == true) {
                RegenerateCodesPhase.CODES_LOST
            } else {
                RegenerateCodesPhase.PASSWORD
            },
        ),
    )
    val uiState: StateFlow<RegenerateCodesUiState> = _uiState.asStateFlow()

    // Único lugar donde viven los códigos: memoria, nunca en UiState ni en SavedStateHandle.
    private val _recoveryCodes = MutableStateFlow<RecoveryCodes?>(null)
    val recoveryCodes: StateFlow<RecoveryCodes?> = _recoveryCodes.asStateFlow()

    fun onFieldEdited() {
        _uiState.update { if (it.error == null) it else it.copy(error = null, retryMinutes = null) }
    }

    fun submit(currentPassword: CharArray) {
        if (!_uiState.value.canSubmit) {
            currentPassword.fill(ZERO)
            return
        }
        val invalid = AuthRules.validateCurrentPassword(currentPassword)
        if (invalid != null) {
            currentPassword.fill(ZERO)
            _uiState.update {
                it.copy(error = regenerateCodesErrorFor(invalid), retryMinutes = null, secretsWiped = it.secretsWiped + 1)
            }
            return
        }
        _uiState.update { it.copy(error = null, retryMinutes = null, isSubmitting = true) }
        viewModelScope.launch {
            try {
                apply(auth.regenerateRecoveryCodes(currentPassword))
            } finally {
                currentPassword.fill(ZERO)
                _uiState.update { it.copy(isSubmitting = false, secretsWiped = it.secretsWiped + 1) }
            }
        }
    }

    fun onSavedCheckedChange(checked: Boolean) {
        _uiState.update { if (it.phase == RegenerateCodesPhase.CODES) it.copy(savedChecked = checked) else it }
    }

    fun onCodesCopied() {
        _uiState.update { if (it.phase == RegenerateCodesPhase.CODES) it.copy(copied = true) else it }
    }

    fun confirmCodesSaved() {
        val current = _uiState.value
        if (current.phase == RegenerateCodesPhase.CODES && current.savedChecked) discardCodesAndFinish()
    }

    fun leaveWithoutSaving() {
        if (_uiState.value.phase == RegenerateCodesPhase.CODES) discardCodesAndFinish()
    }

    fun acknowledgeLostOrMissingCodes() {
        val phase = _uiState.value.phase
        if (phase != RegenerateCodesPhase.CODES_LOST && phase != RegenerateCodesPhase.NO_CODES) return
        savedState.remove<Boolean>(KEY_CODES_PENDING)
        _uiState.update { it.copy(phase = RegenerateCodesPhase.FINISHED) }
    }

    public override fun onCleared() {
        _recoveryCodes.value = null
    }

    private fun discardCodesAndFinish() {
        // Se retira la fase antes que los códigos: `CODES` siempre implica códigos presentes.
        _uiState.update { it.copy(phase = RegenerateCodesPhase.FINISHED) }
        _recoveryCodes.value = null
        savedState.remove<Boolean>(KEY_CODES_PENDING)
    }

    private fun apply(outcome: RegenerateCodesOutcome) {
        when (outcome) {
            is RegenerateCodesOutcome.Regenerated -> {
                _recoveryCodes.value = outcome.codes
                savedState[KEY_CODES_PENDING] = true
                _uiState.update { it.copy(phase = RegenerateCodesPhase.CODES) }
            }
            RegenerateCodesOutcome.RegeneratedWithoutCodes ->
                _uiState.update { it.copy(phase = RegenerateCodesPhase.NO_CODES) }
            is RegenerateCodesOutcome.Failed -> applyFailure(regenerateCodesErrorFor(outcome.error), outcome.retryAfterSeconds)
        }
    }

    private fun applyFailure(error: RegenerateCodesError, retryAfterSeconds: Long?) {
        val rateLimited = error == RegenerateCodesError.RATE_LIMITED
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

    companion object {
        const val KEY_CODES_PENDING = "codes_pending"
        private const val ZERO = '\u0000'
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
