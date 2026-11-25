package com.simposio.taskeer.domain.search

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

fun normalizeForSearch(text: String): String =
    DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

fun searchTerms(query: String): List<String> =
    normalizeForSearch(query).trim().split(WHITESPACE).filter { it.isNotEmpty() }
