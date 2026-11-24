package com.simposio.taskeer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.region.RegionCatalog
import com.simposio.taskeer.domain.region.RegionPreferences
import com.simposio.taskeer.domain.region.formatOffset
import com.simposio.taskeer.domain.region.search
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val onEnabled: suspend (ReminderMechanism) -> Unit,
    private val systemZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val picker = MutableStateFlow<RegionPickerKind?>(null)
    private val query = MutableStateFlow("")

    private val general = combine(
        preferences.themeMode,
        preferences.dynamicColor,
        preferences.deadlineReminders,
        preferences.digest,
        preferences.defaultReminderTime,
    ) { themeMode, dynamicColor, deadline, digest, defaultTime ->
        SettingsUiState(themeMode, dynamicColor, deadline, digest, defaultTime)
    }

    private val regionState = combine(preferences.region, picker, query, ::RegionInputs)

    val uiState: StateFlow<SettingsUiState> = combine(general, regionState) { base, inputs ->
        withRegion(base, inputs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    private data class RegionInputs(
        val region: RegionPreferences,
        val picker: RegionPickerKind?,
        val query: String,
    )

    private fun withRegion(base: SettingsUiState, inputs: RegionInputs): SettingsUiState {
        val now = clock()
        val rows = RegionCatalog.timeZonesAt(now).map { TimeZoneRow(it, formatOffset(it.offsetAt(now))) }
        val selectedZone = RegionCatalog.timeZone(inputs.region.timeZoneIana)
        return base.copy(
            region = inputs.region,
            systemZoneId = systemZone().id,
            selectedCountry = RegionCatalog.country(inputs.region.countryIso),
            selectedTimeZone = rows.firstOrNull { it.option == selectedZone },
            picker = inputs.picker,
            query = inputs.query,
            countries = if (inputs.picker == RegionPickerKind.COUNTRY) {
                RegionCatalog.countries.search(inputs.query)
            } else {
                emptyList()
            },
            timeZones = if (inputs.picker == RegionPickerKind.TIME_ZONE) {
                val matches = rows.map { it.option }.search(inputs.query).toSet()
                rows.filter { it.option in matches }
            } else {
                emptyList()
            },
        )
    }

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

    fun openPicker(kind: RegionPickerKind) {
        query.value = ""
        picker.value = kind
    }

    fun closePicker() {
        picker.value = null
        query.value = ""
    }

    fun setQuery(text: String) {
        query.value = text
    }

    // La UI solo ofrece entradas del catálogo, así que un Failure de validación no puede producirse aquí.
    fun setCountry(isoCode: String?) {
        viewModelScope.launch {
            preferences.setCountry(isoCode)
            closePicker()
        }
    }

    fun setTimeZone(ianaName: String?) {
        viewModelScope.launch {
            preferences.setTimeZone(ianaName)
            closePicker()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}
