package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>

    val dynamicColor: Flow<Boolean>

    val deadlineReminders: Flow<DeadlineReminderSettings>

    val digest: Flow<DigestSettings>

    val defaultReminderTime: Flow<LocalTime>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setDeadlineRemindersEnabled(enabled: Boolean)

    suspend fun setReminderLead(lead: ReminderLead)

    suspend fun setDigestEnabled(enabled: Boolean)

    suspend fun setDigestDays(days: Set<DayOfWeek>)

    suspend fun setDigestTime(time: LocalTime)

    suspend fun setDefaultReminderTime(time: LocalTime)
}
