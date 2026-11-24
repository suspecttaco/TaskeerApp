package com.simposio.taskeer.di

import com.simposio.taskeer.domain.date.TimeZoneProvider
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferredTimeZoneProviderTest {

    private val system = TimeZoneProvider { ZoneId.of("Europe/Madrid") }

    @Test
    fun sinEleccion_usaLaZonaDelSistema() {
        assertEquals(ZoneId.of("Europe/Madrid"), PreferredTimeZoneProvider(system, null).current())
    }

    @Test
    fun conEleccion_usaLaElegida() {
        assertEquals(ZoneId.of("Asia/Tokyo"), PreferredTimeZoneProvider(system, "Asia/Tokyo").current())
    }

    @Test
    fun update_cambiaCurrentYNullVuelveAlSistema() {
        val provider = PreferredTimeZoneProvider(system, null)

        provider.update("America/Mexico_City")
        assertEquals(ZoneId.of("America/Mexico_City"), provider.current())

        provider.update(null)
        assertEquals(ZoneId.of("Europe/Madrid"), provider.current())
    }

    @Test
    fun ianaInvalida_usaLaZonaDelSistema() {
        val provider = PreferredTimeZoneProvider(system, "No/Existe")
        assertEquals(ZoneId.of("Europe/Madrid"), provider.current())

        provider.update("Tampoco/Existe")
        assertEquals(ZoneId.of("Europe/Madrid"), provider.current())
    }

    @Test
    fun changes_emiteAlCambiarYNoAlRepetir() = runTest {
        val provider = PreferredTimeZoneProvider(system, null)
        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { provider.changes.collect { emissions++ } }

        provider.update("Asia/Tokyo")
        provider.update("Asia/Tokyo")
        provider.update("Europe/London")

        assertEquals(2, emissions)
    }

    @Test
    fun zonaDelSistemaCambiante_sigueSiNoHayEleccion() {
        var zone = ZoneId.of("Europe/Madrid")
        val provider = PreferredTimeZoneProvider({ zone }, null)

        zone = ZoneId.of("Asia/Tokyo")

        assertEquals(ZoneId.of("Asia/Tokyo"), provider.current())
    }

    @Test
    fun zonaDelSistema_noAfectaSiHayEleccion() {
        var zone = ZoneId.of("Europe/Madrid")
        val provider = PreferredTimeZoneProvider({ zone }, "America/Bogota")

        zone = ZoneId.of("Asia/Tokyo")

        assertEquals(ZoneId.of("America/Bogota"), provider.current())
    }
}
