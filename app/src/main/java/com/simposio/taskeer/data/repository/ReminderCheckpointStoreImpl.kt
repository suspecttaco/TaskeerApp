package com.simposio.taskeer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.simposio.taskeer.domain.model.ReminderMechanism
import java.time.Instant
import kotlinx.coroutines.flow.first

class ReminderCheckpointStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : ReminderCheckpointStore {

    override suspend fun read(mechanism: ReminderMechanism): Instant? =
        dataStore.data.first()[key(mechanism)]?.let(Instant::ofEpochMilli)

    override suspend fun write(mechanism: ReminderMechanism, at: Instant) {
        dataStore.edit { it[key(mechanism)] = at.toEpochMilli() }
    }

    private fun key(mechanism: ReminderMechanism): Preferences.Key<Long> = when (mechanism) {
        ReminderMechanism.DEADLINE -> KEY_DEADLINE
        ReminderMechanism.DIGEST -> KEY_DIGEST
    }

    private companion object {
        val KEY_DEADLINE = longPreferencesKey("deadline_checkpoint")
        val KEY_DIGEST = longPreferencesKey("digest_checkpoint")
    }
}
