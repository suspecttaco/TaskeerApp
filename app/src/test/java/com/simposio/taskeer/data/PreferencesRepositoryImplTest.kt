package com.simposio.taskeer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
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

    @Test
    fun deadlineReminders_sinValor_estaDesactivadoConUnaHora() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(DeadlineReminderSettings(false, ReminderLead.ONE_HOUR), repository.deadlineReminders.first())
    }

    @Test
    fun deadlineReminders_activarYCambiarAntelacion_seLeeTrasReabrir() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).apply {
            setDeadlineRemindersEnabled(true)
            setReminderLead(ReminderLead.ONE_DAY)
        }

        val reread = PreferencesRepositoryImpl(store).deadlineReminders.first()

        assertEquals(DeadlineReminderSettings(true, ReminderLead.ONE_DAY), reread)
    }

    @Test
    fun deadlineReminders_conAntelacionDesconocida_leeElDefecto() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[stringPreferencesKey("reminder_lead")] = "UNA_SEMANA" }

        val lead = PreferencesRepositoryImpl(store).deadlineReminders.first().lead

        assertEquals(ReminderLead.ONE_HOUR, lead)
    }

    @Test
    fun digest_sinValor_estaDesactivadoDeLunesAViernesALas0900() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        val digest = repository.digest.first()

        assertEquals(DigestSettings(false, DigestSettings.WEEKDAYS, LocalTime.of(9, 0)), digest)
    }

    @Test
    fun digest_persisteActivacionDiasYHoraTrasReabrir() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).apply {
            setDigestEnabled(true)
            setDigestDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY))
            setDigestTime(LocalTime.of(18, 45))
        }

        val reread = PreferencesRepositoryImpl(store).digest.first()

        assertEquals(
            DigestSettings(true, setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), LocalTime.of(18, 45)),
            reread,
        )
    }

    @Test
    fun digest_conjuntoVacioDeDias_esUnValorValido() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setDigestDays(emptySet())

        val days = PreferencesRepositoryImpl(store).digest.first().days

        assertEquals(emptySet<DayOfWeek>(), days)
    }

    @Test
    fun digest_conDiaDesconocido_leeLosDiasPorDefecto() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[stringPreferencesKey("digest_days")] = "MONDAY,FUNDAY" }

        val days = PreferencesRepositoryImpl(store).digest.first().days

        assertEquals(DigestSettings.WEEKDAYS, days)
    }

    @Test
    fun digest_conHoraFueraDeRango_leeLas0900() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[intPreferencesKey("digest_time_minutes")] = 1440 }

        val time = PreferencesRepositoryImpl(store).digest.first().time

        assertEquals(LocalTime.of(9, 0), time)
    }

    @Test
    fun defaultReminderTime_sinValorEsLas0900YPersisteConPrecisionDeMinuto() = runTest {
        val store = newDataStore(backgroundScope)
        assertEquals(LocalTime.of(9, 0), PreferencesRepositoryImpl(store).defaultReminderTime.first())

        PreferencesRepositoryImpl(store).setDefaultReminderTime(LocalTime.of(8, 30))

        assertEquals(LocalTime.of(8, 30), PreferencesRepositoryImpl(store).defaultReminderTime.first())
    }

    @Test
    fun defaultReminderTime_negativaOFueraDeRango_leeElDefecto() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[intPreferencesKey("reminder_default_time_minutes")] = -5 }

        assertEquals(LocalTime.of(9, 0), PreferencesRepositoryImpl(store).defaultReminderTime.first())
    }

    @Test
    fun cambiarLaHoraPorDefecto_noEscribeOtrasClaves() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setDefaultReminderTime(LocalTime.of(8, 30))

        val keys = store.data.first().asMap().keys.map { it.name }

        assertEquals(listOf("reminder_default_time_minutes"), keys)
    }

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { preferencesFile() }

    private fun preferencesFile(): File = File(tempFolder.root, "${PreferencesRepositoryImpl.DATA_STORE_NAME}.preferences_pb")
}
