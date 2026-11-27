package com.simposio.taskeer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.simposio.taskeer.data.repository.PreferencesRepositoryImpl
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.region.RegionPreferences
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun suggestionsLastShownDay_sinValor_esNulo() = runTest {
        assertNull(PreferencesRepositoryImpl(newDataStore(backgroundScope)).suggestionsLastShownDay.first())
    }

    @Test
    fun suggestionsLastShownDay_seLeeDeNuevoYGuardaTextoIso() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setSuggestionsLastShownDay(LocalDate.of(2026, 11, 19))

        assertEquals(LocalDate.of(2026, 11, 19), PreferencesRepositoryImpl(store).suggestionsLastShownDay.first())
        assertEquals(
            "2026-11-19",
            store.data.first()[stringPreferencesKey("suggestions_last_shown_day")],
        )
    }

    @Test
    fun suggestionsLastShownDay_valorInvalido_esNulo() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[stringPreferencesKey("suggestions_last_shown_day")] = "ayer" }

        assertNull(PreferencesRepositoryImpl(store).suggestionsLastShownDay.first())
    }

    @Test
    fun hiddenCalendarIds_sinValor_esVacio() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(emptySet<Long>(), repository.hiddenCalendarIds.first())
    }

    @Test
    fun hiddenCalendarIds_ocultarSeLeeDeNuevo() = runTest {
        val store = newDataStore(backgroundScope)
        val repository = PreferencesRepositoryImpl(store)
        repository.setCalendarHidden(7, hidden = true)
        repository.setCalendarHidden(9, hidden = true)

        assertEquals(setOf(7L, 9L), PreferencesRepositoryImpl(store).hiddenCalendarIds.first())
    }

    @Test
    fun hiddenCalendarIds_desocultarQuitaSoloEseId() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))
        repository.setCalendarHidden(7, hidden = true)
        repository.setCalendarHidden(9, hidden = true)

        repository.setCalendarHidden(7, hidden = false)

        assertEquals(setOf(9L), repository.hiddenCalendarIds.first())
    }

    @Test
    fun hiddenCalendarIds_ocultarDosVecesNoDuplicaYDesocultarElUltimoVacia() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))
        repository.setCalendarHidden(7, hidden = true)
        repository.setCalendarHidden(7, hidden = true)
        assertEquals(setOf(7L), repository.hiddenCalendarIds.first())

        repository.setCalendarHidden(7, hidden = false)

        assertEquals(emptySet<Long>(), repository.hiddenCalendarIds.first())
    }

    @Test
    fun hiddenCalendarIds_valoresNoNumericosSeIgnoran() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit { it[stringSetPreferencesKey("calendar_hidden_ids")] = setOf("5", "abc", "") }

        assertEquals(setOf(5L), PreferencesRepositoryImpl(store).hiddenCalendarIds.first())
    }

    @Test
    fun region_porDefectoEstaVacia() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(RegionPreferences(null, null), repository.region.first())
    }

    @Test
    fun region_persisteYSeRelee() = runTest {
        val store = newDataStore(backgroundScope)
        val repository = PreferencesRepositoryImpl(store)
        assertEquals(DomainResult.Success(Unit), repository.setCountry("MX"))
        assertEquals(DomainResult.Success(Unit), repository.setTimeZone("America/Mexico_City"))

        val reread = PreferencesRepositoryImpl(store).region.first()

        assertEquals(RegionPreferences("MX", "America/Mexico_City"), reread)
    }

    @Test
    fun region_conNullBorraLaPreferencia() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))
        repository.setCountry("MX")
        repository.setTimeZone("Asia/Tokyo")

        repository.setCountry(null)
        repository.setTimeZone(null)

        assertEquals(RegionPreferences(null, null), repository.region.first())
    }

    @Test
    fun region_claveFueraDeCatalogo_devuelveValidacionSinCambiarNada() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))
        repository.setCountry("MX")
        repository.setTimeZone("Asia/Tokyo")

        val country = repository.setCountry("ZZ")
        val zone = repository.setTimeZone("X/Y")

        assertEquals(DomainResult.Failure(DomainError.Validation("INVALID_COUNTRY", "País no válido")), country)
        assertEquals(DomainResult.Failure(DomainError.Validation("INVALID_TIMEZONE", "Zona horaria no válida")), zone)
        assertEquals(RegionPreferences("MX", "Asia/Tokyo"), repository.region.first())
    }

    @Test
    fun region_valorDesconocidoEnDisco_seLeeNullYNoSeBorra() = runTest {
        val store = newDataStore(backgroundScope)
        store.edit {
            it[stringPreferencesKey("region_country_iso")] = "ZZ"
            it[stringPreferencesKey("region_timezone_iana")] = "X/Y"
        }

        assertEquals(RegionPreferences(null, null), PreferencesRepositoryImpl(store).region.first())
        val raw = store.data.first()
        assertEquals("ZZ", raw[stringPreferencesKey("region_country_iso")])
        assertEquals("X/Y", raw[stringPreferencesKey("region_timezone_iana")])
    }

    @Test
    fun sincronizacionEnSegundoPlano_porDefectoActivada() = runTest {
        val repository = PreferencesRepositoryImpl(newDataStore(backgroundScope))

        assertEquals(true, repository.backgroundSync.first())
    }

    @Test
    fun setBackgroundSyncEnabled_sePersisteAlRecrearElRepositorio() = runTest {
        val store = newDataStore(backgroundScope)
        PreferencesRepositoryImpl(store).setBackgroundSyncEnabled(false)

        assertEquals(false, PreferencesRepositoryImpl(store).backgroundSync.first())
    }

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { preferencesFile() }

    private fun preferencesFile(): File = File(tempFolder.root, "${PreferencesRepositoryImpl.DATA_STORE_NAME}.preferences_pb")
}
