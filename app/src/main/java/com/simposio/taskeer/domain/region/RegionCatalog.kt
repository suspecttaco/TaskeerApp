package com.simposio.taskeer.domain.region

import java.text.Collator
import java.time.Instant
import java.util.Locale

object RegionCatalog {
    private val spanish: Locale = Locale.Builder().setLanguage("es").build()

    val countries: List<Country> by lazy {
        val collator = Collator.getInstance(spanish)
        Locale.getISOCountries()
            .map { iso -> Country(iso, countryName(iso), flagEmoji(iso)) }
            .filter { it.name.isNotBlank() && it.name != it.isoCode }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    val timeZones: List<TimeZoneOption> = listOf(
        TimeZoneOption("Pacific/Honolulu", "Honolulu"),
        TimeZoneOption("America/Anchorage", "Anchorage"),
        TimeZoneOption("America/Los_Angeles", "Los Ángeles"),
        TimeZoneOption("America/Denver", "Denver"),
        TimeZoneOption("America/Phoenix", "Phoenix"),
        TimeZoneOption("America/Mexico_City", "Ciudad de México"),
        TimeZoneOption("America/Chicago", "Chicago"),
        TimeZoneOption("America/Bogota", "Bogotá"),
        TimeZoneOption("America/New_York", "Nueva York"),
        TimeZoneOption("America/Caracas", "Caracas"),
        TimeZoneOption("America/Halifax", "Halifax"),
        TimeZoneOption("America/Sao_Paulo", "São Paulo"),
        TimeZoneOption("America/Argentina/Buenos_Aires", "Buenos Aires"),
        TimeZoneOption("UTC", "UTC"),
        TimeZoneOption("Europe/London", "Londres"),
        TimeZoneOption("Europe/Madrid", "Madrid"),
        TimeZoneOption("Africa/Cairo", "El Cairo"),
        TimeZoneOption("Europe/Moscow", "Moscú"),
        TimeZoneOption("Asia/Dubai", "Dubái"),
        TimeZoneOption("Asia/Kolkata", "Calcuta"),
        TimeZoneOption("Asia/Shanghai", "Shanghái"),
        TimeZoneOption("Asia/Tokyo", "Tokio"),
        TimeZoneOption("Australia/Sydney", "Sídney"),
        TimeZoneOption("Pacific/Auckland", "Auckland"),
    )

    fun timeZonesAt(now: Instant): List<TimeZoneOption> =
        timeZones.sortedWith(compareBy<TimeZoneOption> { it.offsetAt(now).totalSeconds }.thenBy { it.displayName })

    fun country(isoCode: String?): Country? = isoCode?.let { iso -> countries.firstOrNull { it.isoCode == iso } }

    fun timeZone(ianaName: String?): TimeZoneOption? =
        ianaName?.let { name -> timeZones.firstOrNull { it.ianaName == name } }

    private fun countryName(isoCode: String): String =
        Locale.Builder().setRegion(isoCode).build().getDisplayCountry(spanish)
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6

fun flagEmoji(isoCode: String): String = isoCode.uppercase()
    .map { Character.toChars(REGIONAL_INDICATOR_A + (it - 'A')).concatToString() }
    .joinToString("")
