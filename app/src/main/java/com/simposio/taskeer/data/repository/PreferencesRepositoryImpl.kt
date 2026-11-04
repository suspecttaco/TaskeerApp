package com.simposio.taskeer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val themeMode: Flow<ThemeMode> = readPreferences()
        .map { ThemeMode.fromStorage(it[KEY_THEME_MODE]) }

    override val dynamicColor: Flow<Boolean> = readPreferences()
        .map { it[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR }

    override val deadlineReminders: Flow<DeadlineReminderSettings> = readPreferences()
        .map {
            DeadlineReminderSettings(
                enabled = it[KEY_DEADLINE_ENABLED] ?: false,
                lead = ReminderLead.fromStorage(it[KEY_REMINDER_LEAD]),
            )
        }

    override val digest: Flow<DigestSettings> = readPreferences()
        .map {
            DigestSettings(
                enabled = it[KEY_DIGEST_ENABLED] ?: false,
                days = parseDays(it[KEY_DIGEST_DAYS]),
                time = parseTime(it[KEY_DIGEST_TIME_MINUTES], DEFAULT_REMINDER_TIME),
            )
        }

    override val defaultReminderTime: Flow<LocalTime> = readPreferences()
        .map { parseTime(it[KEY_DEFAULT_REMINDER_TIME_MINUTES], DEFAULT_REMINDER_TIME) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setDeadlineRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DEADLINE_ENABLED] = enabled }
    }

    override suspend fun setReminderLead(lead: ReminderLead) {
        dataStore.edit { it[KEY_REMINDER_LEAD] = lead.name }
    }

    override suspend fun setDigestEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DIGEST_ENABLED] = enabled }
    }

    override suspend fun setDigestDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[KEY_DIGEST_DAYS] = days.sorted().joinToString(",") { day -> day.name } }
    }

    override suspend fun setDigestTime(time: LocalTime) {
        dataStore.edit { it[KEY_DIGEST_TIME_MINUTES] = time.toMinutes() }
    }

    override suspend fun setDefaultReminderTime(time: LocalTime) {
        dataStore.edit { it[KEY_DEFAULT_REMINDER_TIME_MINUTES] = time.toMinutes() }
    }

    private fun readPreferences(): Flow<Preferences> = dataStore.data
        .catch { cause ->
            // Un fichero ilegible no debe tumbar el arranque: se usan las preferencias por defecto.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }

    // Cadena vacía = ningún día (válido); un nombre desconocido invalida todo el valor.
    private fun parseDays(stored: String?): Set<DayOfWeek> {
        if (stored == null) {
            return DigestSettings.WEEKDAYS
        }
        if (stored.isEmpty()) {
            return emptySet()
        }
        val days = stored.split(",").map { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        return if (days.any { it == null }) DigestSettings.WEEKDAYS else days.filterNotNull().toSet()
    }

    private fun parseTime(minutes: Int?, default: LocalTime): LocalTime =
        if (minutes != null && minutes in 0 until MINUTES_PER_DAY) {
            LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        } else {
            default
        }

    private fun LocalTime.toMinutes(): Int = hour * MINUTES_PER_HOUR + minute

    companion object {
        const val DATA_STORE_NAME: String = "user_preferences"
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true

        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        private val KEY_DEADLINE_ENABLED = booleanPreferencesKey("deadline_reminders_enabled")
        private val KEY_REMINDER_LEAD = stringPreferencesKey("reminder_lead")
        private val KEY_DIGEST_ENABLED = booleanPreferencesKey("digest_enabled")
        private val KEY_DIGEST_DAYS = stringPreferencesKey("digest_days")
        private val KEY_DIGEST_TIME_MINUTES = intPreferencesKey("digest_time_minutes")
        private val KEY_DEFAULT_REMINDER_TIME_MINUTES =
            intPreferencesKey("reminder_default_time_minutes")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
