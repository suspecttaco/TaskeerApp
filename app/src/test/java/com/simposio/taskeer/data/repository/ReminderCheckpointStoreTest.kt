package com.simposio.taskeer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.simposio.taskeer.domain.model.ReminderMechanism
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReminderCheckpointStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val at: Instant = Instant.parse("2026-11-04T10:00:00Z")

    @Test
    fun read_sinEscrituraPrevia_devuelveNull() = runTest {
        val store = ReminderCheckpointStoreImpl(newDataStore(backgroundScope))

        assertNull(store.read(ReminderMechanism.DEADLINE))
        assertNull(store.read(ReminderMechanism.DIGEST))
    }

    @Test
    fun write_seLeeTrasReabrirElAlmacen() = runTest {
        val dataStore = newDataStore(backgroundScope)
        ReminderCheckpointStoreImpl(dataStore).write(ReminderMechanism.DEADLINE, at)

        assertEquals(at, ReminderCheckpointStoreImpl(dataStore).read(ReminderMechanism.DEADLINE))
    }

    @Test
    fun write_deUnMecanismo_noAlteraElOtro() = runTest {
        val store = ReminderCheckpointStoreImpl(newDataStore(backgroundScope))
        store.write(ReminderMechanism.DEADLINE, at)

        assertNull(store.read(ReminderMechanism.DIGEST))

        store.write(ReminderMechanism.DIGEST, at.plusSeconds(5))

        assertEquals(at, store.read(ReminderMechanism.DEADLINE))
        assertEquals(at.plusSeconds(5), store.read(ReminderMechanism.DIGEST))
    }

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(tempFolder.root, "checkpoints.preferences_pb") }
}
