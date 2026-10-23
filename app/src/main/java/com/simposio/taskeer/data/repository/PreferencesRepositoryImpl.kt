package com.simposio.taskeer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simposio.taskeer.domain.model.ThemeMode
import java.io.IOException
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

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private fun readPreferences(): Flow<Preferences> = dataStore.data
        .catch { cause ->
            // Un fichero ilegible no debe tumbar el arranque: se usan las preferencias por defecto.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }

    companion object {
        const val DATA_STORE_NAME: String = "user_preferences"
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
