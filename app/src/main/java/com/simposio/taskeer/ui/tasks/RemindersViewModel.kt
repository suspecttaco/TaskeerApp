package com.simposio.taskeer.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import com.simposio.taskeer.domain.date.DateTimeConverter
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.reminder.laterToday
import com.simposio.taskeer.domain.reminder.nextWeekAt
import com.simposio.taskeer.domain.reminder.tomorrowAt
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val repository: ReminderRepository,
    private val preferences: PreferencesRepository,
    private val timeZone: TimeZoneProvider,
    private val dateTimeConverter: DateTimeConverter,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())

    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    fun open(taskId: String) {
        collectJob?.cancel()
        _uiState.value = RemindersUiState(taskId = taskId)
        collectJob = viewModelScope.launch {
            repository.observeReminder(taskId).collect { reminder ->
                _uiState.update {
                    it.copy(
                        reminder = reminder,
                        reminderAtLocal = reminder?.remindAt?.let(dateTimeConverter::toLocalDateTime),
                    )
                }
            }
        }
    }

    fun close() {
        collectJob?.cancel()
        collectJob = null
        _uiState.value = RemindersUiState()
    }

    fun laterToday() = create { laterToday(clock()) }

    fun tomorrow() = create { tomorrowAt(clock(), timeZone.current(), defaultTime()) }

    fun nextWeek() = create { nextWeekAt(clock(), timeZone.current(), defaultTime()) }

    fun custom(local: LocalDateTime) = create { dateTimeConverter.toInstant(local) }

    fun cancel() {
        val reminder = _uiState.value.reminder ?: return
        viewModelScope.launch {
            when (val result = repository.cancelReminder(reminder.id)) {
                is DomainResult.Success -> _uiState.update { it.copy(error = null) }
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error.message) }
            }
        }
    }

    private suspend fun defaultTime(): LocalTime = preferences.defaultReminderTime.first()

    private fun create(remindAt: suspend () -> Instant) {
        val taskId = _uiState.value.taskId ?: return
        viewModelScope.launch {
            when (val result = repository.createReminder(taskId, remindAt())) {
                is DomainResult.Success -> _uiState.update { it.copy(error = null) }
                is DomainResult.Failure -> _uiState.update { it.copy(error = result.error.message) }
            }
        }
    }
}
