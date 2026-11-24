package com.simposio.taskeer.data.repository

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.region.RegionPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>

    val dynamicColor: Flow<Boolean>

    val deadlineReminders: Flow<DeadlineReminderSettings>

    val digest: Flow<DigestSettings>

    val defaultReminderTime: Flow<LocalTime>

    val suggestionsLastShownDay: Flow<LocalDate?>

    val hiddenCalendarIds: Flow<Set<Long>>

    val region: Flow<RegionPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setDeadlineRemindersEnabled(enabled: Boolean)

    suspend fun setReminderLead(lead: ReminderLead)

    suspend fun setDigestEnabled(enabled: Boolean)

    suspend fun setDigestDays(days: Set<DayOfWeek>)

    suspend fun setDigestTime(time: LocalTime)

    suspend fun setDefaultReminderTime(time: LocalTime)

    suspend fun setSuggestionsLastShownDay(day: LocalDate)

    suspend fun setCalendarHidden(id: Long, hidden: Boolean)

    suspend fun setCountry(isoCode: String?): DomainResult<Unit>

    suspend fun setTimeZone(ianaName: String?): DomainResult<Unit>
}
