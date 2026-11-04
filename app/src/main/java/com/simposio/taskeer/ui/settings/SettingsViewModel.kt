package com.simposio.taskeer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val onEnabled: suspend (ReminderMechanism) -> Unit,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.themeMode,
        preferences.dynamicColor,
        preferences.deadlineReminders,
        preferences.digest,
        preferences.defaultReminderTime,
    ) { themeMode, dynamicColor, deadline, digest, defaultTime ->
        SettingsUiState(themeMode, dynamicColor, deadline, digest, defaultTime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setDeadlineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDeadlineRemindersEnabled(enabled)
            if (enabled) {
                onEnabled(ReminderMechanism.DEADLINE)
            }
        }
    }

    fun setLead(lead: ReminderLead) {
        viewModelScope.launch { preferences.setReminderLead(lead) }
    }

    fun setDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDigestEnabled(enabled)
            if (enabled) {
                onEnabled(ReminderMechanism.DIGEST)
            }
        }
    }

    fun setDigestDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { preferences.setDigestDays(days) }
    }

    fun setDigestTime(time: LocalTime) {
        viewModelScope.launch { preferences.setDigestTime(time) }
    }

    fun setDefaultReminderTime(time: LocalTime) {
        viewModelScope.launch { preferences.setDefaultReminderTime(time) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
