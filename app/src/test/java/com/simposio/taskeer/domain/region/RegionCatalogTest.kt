package com.simposio.taskeer.domain.region

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionCatalogTest {

    @Test
    fun paises_sinCodigosRepetidosNiNombresVacios() {
        val countries = RegionCatalog.countries

        assertTrue(countries.isNotEmpty())
        assertEquals(countries.size, countries.map { it.isoCode }.toSet().size)
        assertTrue(countries.all { it.name.isNotBlank() && it.isoCode.matches(Regex("[A-Z]{2}")) })
    }

    @Test
    fun paises_estanOrdenadosAlfabeticamenteEnEspanol() {
        val names = RegionCatalog.countries.map { it.name }
        val collator = java.text.Collator.getInstance(java.util.Locale.Builder().setLanguage("es").build())

        assertEquals(names.sortedWith { a, b -> collator.compare(a, b) }, names)
    }

    @Test
    fun pais_mexicoSeLlamaMexicoYTieneBanderaDeDosPuntosDeCodigo() {
        val mexico = RegionCatalog.country("MX")

        assertEquals("México", mexico?.name)
        assertEquals("🇲🇽", mexico?.flagEmoji)
        assertEquals(2, mexico?.flagEmoji?.codePointCount(0, mexico.flagEmoji.length))
    }

    @Test
    fun zonas_sonVeinticuatroValidasYSinDuplicados() {
        val zones = RegionCatalog.timeZones

        assertEquals(24, zones.size)
        assertEquals(24, zones.map { it.ianaName }.toSet().size)
        zones.forEach { assertEquals(it.ianaName, ZoneId.of(it.ianaName).id) }
    }

    @Test
    fun zonasPorDesplazamiento_ordenanDeMenosAMasUtc() {
        val offsets = RegionCatalog.timeZonesAt(Instant.parse("2026-01-15T12:00:00Z"))
            .map { it.offsetAt(Instant.parse("2026-01-15T12:00:00Z")).totalSeconds }

        assertEquals(offsets.sorted(), offsets)
        assertEquals("Pacific/Honolulu", RegionCatalog.timeZonesAt(Instant.parse("2026-01-15T12:00:00Z")).first().ianaName)
    }

    @Test
    fun zona_respetaElHorarioDeVerano() {
        val newYork = RegionCatalog.timeZone("America/New_York")!!

        assertEquals(-5 * 3_600, newYork.offsetAt(Instant.parse("2026-01-15T12:00:00Z")).totalSeconds)
        assertEquals(-4 * 3_600, newYork.offsetAt(Instant.parse("2026-07-15T12:00:00Z")).totalSeconds)
    }

    @Test
    fun clavesDesconocidas_devuelvenNull() {
        assertNull(RegionCatalog.country("ZZ"))
        assertNull(RegionCatalog.country(null))
        assertNull(RegionCatalog.timeZone("X/Y"))
        assertNull(RegionCatalog.timeZone(null))
    }
}
