package com.simposio.taskeer.testing

import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.region.RegionCatalog
import com.simposio.taskeer.domain.region.RegionPreferences
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
    hiddenCalendars: Set<Long> = emptySet(),
    region: RegionPreferences = RegionPreferences(),
) : PreferencesRepository {

    private val mode = MutableStateFlow(ThemeMode.AUTO)
    private val dynamic = MutableStateFlow(true)
    private val deadlineState = MutableStateFlow(deadline)
    private val digestState = MutableStateFlow(digest)
    private val defaultTimeState = MutableStateFlow(defaultTime)
    private val lastShownDayState = MutableStateFlow(lastShownDay)
    private val hiddenCalendarState = MutableStateFlow(hiddenCalendars)
    private val regionState = MutableStateFlow(region)

    var failOnSetLastShownDay: Boolean = false

    override val themeMode: Flow<ThemeMode> = mode
    override val dynamicColor: Flow<Boolean> = dynamic
    override val deadlineReminders: Flow<DeadlineReminderSettings> = deadlineState
    override val digest: Flow<DigestSettings> = digestState
    override val defaultReminderTime: Flow<LocalTime> = defaultTimeState
    override val suggestionsLastShownDay: Flow<LocalDate?> = lastShownDayState
    override val hiddenCalendarIds: Flow<Set<Long>> = hiddenCalendarState
    override val region: Flow<RegionPreferences> = regionState

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

    override suspend fun setCalendarHidden(id: Long, hidden: Boolean) {
        hiddenCalendarState.update { if (hidden) it + id else it - id }
    }

    override suspend fun setCountry(isoCode: String?): DomainResult<Unit> {
        if (isoCode != null && RegionCatalog.country(isoCode) == null) {
            return DomainResult.Failure(DomainError.Validation("INVALID_COUNTRY", "País no válido"))
        }
        regionState.update { it.copy(countryIso = isoCode) }
        return DomainResult.Success(Unit)
    }

    override suspend fun setTimeZone(ianaName: String?): DomainResult<Unit> {
        if (ianaName != null && RegionCatalog.timeZone(ianaName) == null) {
            return DomainResult.Failure(DomainError.Validation("INVALID_TIMEZONE", "Zona horaria no válida"))
        }
        regionState.update { it.copy(timeZoneIana = ianaName) }
        return DomainResult.Success(Unit)
    }
}
