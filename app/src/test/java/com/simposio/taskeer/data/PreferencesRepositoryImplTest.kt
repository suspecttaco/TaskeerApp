package com.simposio.taskeer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sinValor_esAuto() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(ThemeMode.AUTO, repository.themeMode.first())
    }

    @Test
    fun sinValor_esMaterialYou() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(true, repository.dynamicColor.first())
    }

    @Test
    fun setThemeMode_seLeeDeNuevo() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setThemeMode(ThemeMode.DARK)

        val reread = PreferencesRepositoryImpl(store).themeMode.first()

        assertEquals(ThemeMode.DARK, reread)
    }

    @Test
    fun setDynamicColor_seLeeDeNuevo() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setDynamicColor(false)

        val reread = PreferencesRepositoryImpl(store).dynamicColor.first()

        assertEquals(false, reread)
    }

    @Test
    fun setThemeMode_escribeElFichero() = runTest {
        PreferencesRepositoryImpl(newDataStore(backgroundScope)).setThemeMode(ThemeMode.LIGHT)

        val file = preferencesFile()
        assertTrue("debe existir el fichero persistido", file.exists())
        assertTrue("el fichero persistido no puede estar vacío", file.length() > 0)
    }

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { preferencesFile() }

    private fun preferencesFile(): File = File(tempFolder.root, "${PreferencesRepositoryImpl.DATA_STORE_NAME}.preferences_pb")
}
