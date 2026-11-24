package com.simposio.taskeer.domain.region

import java.text.Normalizer
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

private val DIACRITICS = Regex("\\p{Mn}+")

private fun normalize(text: String): String =
    DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

@JvmName("searchCountries")
fun List<Country>.search(query: String): List<Country> {
    val needle = normalize(query.trim())
    if (needle.isEmpty()) return this
    return filter { normalize(it.name).contains(needle) || normalize(it.isoCode).contains(needle) }
}

@JvmName("searchTimeZones")
fun List<TimeZoneOption>.search(query: String): List<TimeZoneOption> {
    val needle = normalize(query.trim())
    if (needle.isEmpty()) return this
    return filter { normalize(it.displayName).contains(needle) || normalize(it.ianaName).contains(needle) }
}

fun formatOffset(offset: ZoneOffset): String {
    val total = offset.totalSeconds
    if (total == 0) return "UTC"
    val sign = if (total < 0) "−" else "+"
    val abs = abs(total)
    val hours = abs / SECONDS_PER_HOUR
    val minutes = abs % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return "UTC%s%02d:%02d".format(Locale.ROOT, sign, hours, minutes)
}

private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60
