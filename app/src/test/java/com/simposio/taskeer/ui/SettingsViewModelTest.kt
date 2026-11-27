package com.simposio.taskeer.ui

import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.domain.model.ReminderMechanism
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.region.RegionPreferences
import com.simposio.taskeer.testing.FakePreferencesRepository
import com.simposio.taskeer.ui.settings.RegionPickerKind
import com.simposio.taskeer.ui.settings.SettingsViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val preferences = FakePreferencesRepository()
    private val enabled = mutableListOf<ReminderMechanism>()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(
            preferences,
            { enabled += it },
            { ZoneId.of("Europe/Madrid") },
            { Instant.parse("2026-01-15T12:00:00Z") },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.observe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun uiState_inicial_reflejaLasPreferencias() = runTest {
        observe()

        val state = viewModel.uiState.value

        assertEquals(ThemeMode.AUTO, state.themeMode)
        assertEquals(DeadlineReminderSettings(false, ReminderLead.ONE_HOUR), state.deadline)
        assertEquals(DigestSettings(false, DigestSettings.WEEKDAYS, LocalTime.of(9, 0)), state.digest)
        assertEquals(LocalTime.of(9, 0), state.defaultReminderTime)
    }

    @Test
    fun sincronizacionEnSegundoPlano_porDefectoActivadaYEscribeAlCambiar() = runTest {
        observe()
        assertEquals(true, viewModel.uiState.value.backgroundSync)

        viewModel.onBackgroundSyncChange(false)

        assertEquals(false, viewModel.uiState.value.backgroundSync)
        assertEquals(false, preferences.backgroundSync.first())
    }

    @Test
    fun temaYColorDinamico_persistenEnLasPreferencias() = runTest {
        observe()

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setDynamicColor(false)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(false, viewModel.uiState.value.dynamicColor)
    }

    @Test
    fun activarElAvisoPorFechaLimite_persisteYLlamaAOnEnabledDelMecanismoCorrecto() = runTest {
        observe()

        viewModel.setDeadlineEnabled(true)

        assertEquals(true, viewModel.uiState.value.deadline.enabled)
        assertEquals(listOf(ReminderMechanism.DEADLINE), enabled)
    }

    @Test
    fun activarElResumen_persisteYLlamaAOnEnabledDelMecanismoCorrecto() = runTest {
        observe()

        viewModel.setDigestEnabled(true)

        assertEquals(true, viewModel.uiState.value.digest.enabled)
        assertEquals(listOf(ReminderMechanism.DIGEST), enabled)
    }

    @Test
    fun desactivarUnMecanismo_persisteSinLlamarAOnEnabled() = runTest {
        observe()
        viewModel.setDeadlineEnabled(true)
        viewModel.setDigestEnabled(true)
        enabled.clear()

        viewModel.setDeadlineEnabled(false)
        viewModel.setDigestEnabled(false)

        assertEquals(false, viewModel.uiState.value.deadline.enabled)
        assertEquals(false, viewModel.uiState.value.digest.enabled)
        assertEquals(emptyList<ReminderMechanism>(), enabled)
    }

    @Test
    fun antelacionDiasHoraYHoraPorDefecto_persisten() = runTest {
        observe()

        viewModel.setLead(ReminderLead.ONE_DAY)
        viewModel.setDigestDays(setOf(DayOfWeek.SUNDAY))
        viewModel.setDigestTime(LocalTime.of(7, 45))
        viewModel.setDefaultReminderTime(LocalTime.of(8, 30))

        val state = viewModel.uiState.value
        assertEquals(ReminderLead.ONE_DAY, state.deadline.lead)
        assertEquals(setOf(DayOfWeek.SUNDAY), state.digest.days)
        assertEquals(LocalTime.of(7, 45), state.digest.time)
        assertEquals(LocalTime.of(8, 30), state.defaultReminderTime)
    }

    @Test
    fun diasVacios_sonUnValorValido() = runTest {
        observe()

        viewModel.setDigestDays(emptySet())

        assertEquals(emptySet<DayOfWeek>(), viewModel.uiState.value.digest.days)
    }

    @Test
    fun region_inicial_sinEleccionYConLaZonaDelSistema() = runTest {
        observe()

        val state = viewModel.uiState.value

        assertEquals(RegionPreferences(null, null), state.region)
        assertNull(state.selectedCountry)
        assertNull(state.selectedTimeZone)
        assertEquals("Europe/Madrid", state.systemZoneId)
    }

    @Test
    fun setCountryYSetTimeZone_delegaYReflejaElValorConEtiqueta() = runTest {
        observe()

        viewModel.setCountry("MX")
        viewModel.setTimeZone("America/Mexico_City")

        val state = viewModel.uiState.value
        assertEquals(RegionPreferences("MX", "America/Mexico_City"), state.region)
        assertEquals("México", state.selectedCountry?.name)
        assertEquals("UTC−06:00", state.selectedTimeZone?.offsetLabel)
    }

    @Test
    fun setConNull_quitaLaEleccion() = runTest {
        observe()
        viewModel.setCountry("MX")
        viewModel.setTimeZone("Asia/Tokyo")

        viewModel.setCountry(null)
        viewModel.setTimeZone(null)

        assertEquals(RegionPreferences(null, null), viewModel.uiState.value.region)
    }

    @Test
    fun abrirYCerrarElSelector_reiniciaLaBusqueda() = runTest {
        observe()

        viewModel.openPicker(RegionPickerKind.COUNTRY)
        viewModel.setQuery("mex")
        assertEquals(RegionPickerKind.COUNTRY, viewModel.uiState.value.picker)
        assertEquals("mex", viewModel.uiState.value.query)

        viewModel.closePicker()
        assertNull(viewModel.uiState.value.picker)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun filtradoDePaises_sigueAlTextoDeBusqueda() = runTest {
        observe()
        viewModel.openPicker(RegionPickerKind.COUNTRY)
        val all = viewModel.uiState.value.countries.size

        viewModel.setQuery("MEXICO")

        val filtered = viewModel.uiState.value.countries
        assertTrue(filtered.size < all)
        assertTrue(filtered.any { it.isoCode == "MX" })
        assertEquals(emptyList<Any>(), viewModel.uiState.value.timeZones)
    }

    @Test
    fun filtradoDeZonas_ordenaPorDesplazamientoYFiltraPorTexto() = runTest {
        observe()
        viewModel.openPicker(RegionPickerKind.TIME_ZONE)

        assertEquals("Pacific/Honolulu", viewModel.uiState.value.timeZones.first().option.ianaName)
        assertEquals(24, viewModel.uiState.value.timeZones.size)

        viewModel.setQuery("york")

        assertEquals(listOf("America/New_York"), viewModel.uiState.value.timeZones.map { it.option.ianaName })
    }

    @Test
    fun elegirUnaEntrada_cierraElSelector() = runTest {
        observe()
        viewModel.openPicker(RegionPickerKind.TIME_ZONE)

        viewModel.setTimeZone("Asia/Tokyo")

        assertNull(viewModel.uiState.value.picker)
        assertEquals("Asia/Tokyo", viewModel.uiState.value.region.timeZoneIana)
    }
}
