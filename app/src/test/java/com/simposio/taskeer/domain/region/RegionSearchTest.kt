package com.simposio.taskeer.domain.region

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionSearchTest {

    private val countries = RegionCatalog.countries
    private val zones = RegionCatalog.timeZones

    @Test
    fun paises_sinAcentosNiMayusculas_encuentraMexico() {
        assertTrue(countries.search("MEXICO").any { it.isoCode == "MX" })
    }

    @Test
    fun paises_porIsoCode() {
        assertTrue(countries.search("mx").any { it.isoCode == "MX" })
    }

    @Test
    fun paises_espanaSinEnie_encuentraEspana() {
        assertTrue(countries.search("espana").any { it.isoCode == "ES" })
    }

    @Test
    fun zonas_porNombreIanaYSinAcentos() {
        assertEquals(listOf("America/New_York"), zones.search("york").map { it.ianaName })
        assertEquals(listOf("America/Bogota"), zones.search("bogota").map { it.ianaName })
    }

    @Test
    fun consultaVacia_devuelveTodoElCatalogo() {
        assertEquals(countries, countries.search("  "))
        assertEquals(zones, zones.search(""))
    }

    @Test
    fun sinCoincidencias_devuelveVacio() {
        assertEquals(emptyList<Country>(), countries.search("zzzzqq"))
        assertEquals(emptyList<TimeZoneOption>(), zones.search("zzzzqq"))
    }

    @Test
    fun formatOffset_formaCanonica() {
        assertEquals("UTC−06:00", formatOffset(ZoneOffset.ofHours(-6)))
        assertEquals("UTC+05:30", formatOffset(ZoneOffset.ofHoursMinutes(5, 30)))
        assertEquals("UTC", formatOffset(ZoneOffset.UTC))
    }
}
