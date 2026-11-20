package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository(
    deadline: DeadlineReminderSettings = DeadlineReminderSettings(),
    digest: DigestSettings = DigestSettings(),
    defaultTime: LocalTime = DEFAULT_REMINDER_TIME,
    lastShownDay: LocalDate? = null,
) : PreferencesRepository {

    private val mode = MutableStateFlow(ThemeMode.AUTO)
    private val dynamic = MutableStateFlow(true)
    private val deadlineState = MutableStateFlow(deadline)
    private val digestState = MutableStateFlow(digest)
    private val defaultTimeState = MutableStateFlow(defaultTime)
    private val lastShownDayState = MutableStateFlow(lastShownDay)

    var failOnSetLastShownDay: Boolean = false

    override val themeMode: Flow<ThemeMode> = mode
    override val dynamicColor: Flow<Boolean> = dynamic
    override val deadlineReminders: Flow<DeadlineReminderSettings> = deadlineState
    override val digest: Flow<DigestSettings> = digestState
    override val defaultReminderTime: Flow<LocalTime> = defaultTimeState
    override val suggestionsLastShownDay: Flow<LocalDate?> = lastShownDayState

    override suspend fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamic.value = enabled
    }

    override suspend fun setDeadlineRemindersEnabled(enabled: Boolean) {
        deadlineState.update { it.copy(enabled = enabled) }
    }

    override suspend fun setReminderLead(lead: ReminderLead) {
        deadlineState.update { it.copy(lead = lead) }
    }

    override suspend fun setDigestEnabled(enabled: Boolean) {
        digestState.update { it.copy(enabled = enabled) }
    }

    override suspend fun setDigestDays(days: Set<DayOfWeek>) {
        digestState.update { it.copy(days = days) }
    }

    override suspend fun setDigestTime(time: LocalTime) {
        digestState.update { it.copy(time = time) }
    }

    override suspend fun setDefaultReminderTime(time: LocalTime) {
        defaultTimeState.value = time
    }

    override suspend fun setSuggestionsLastShownDay(day: LocalDate) {
        if (failOnSetLastShownDay) throw IOException("escritura de DataStore fallida")
        lastShownDayState.value = day
    }
}
