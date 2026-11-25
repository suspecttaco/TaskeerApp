package com.simposio.taskeer.domain

import com.simposio.taskeer.domain.search.normalizeForSearch
import com.simposio.taskeer.domain.search.searchTerms
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {

    @Test
    fun normalizeForSearch_quitaAcentosYMayusculas() {
        assertEquals("cancion", normalizeForSearch("Canción"))
        assertEquals("arbol", normalizeForSearch("ÁRBOL"))
    }

    @Test
    fun searchTerms_separaYDescartaVacios() {
        assertEquals(listOf("compra", "leche"), searchTerms("  Compra \t LECHE  "))
        assertEquals(emptyList<String>(), searchTerms("   "))
    }
}
