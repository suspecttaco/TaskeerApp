package com.simposio.taskeer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseKindTest {

    @Test
    fun cargando_esLoading() {
        assertEquals(PhaseKind.LOADING, phaseKindOf(loading = true, error = false))
    }

    @Test
    fun error_esError() {
        assertEquals(PhaseKind.ERROR, phaseKindOf(loading = false, error = true))
    }

    @Test
    fun vacioOLista_sonContenidoSinFundidoEntreSi() {
        assertEquals(PhaseKind.CONTENT, phaseKindOf(loading = false, error = false))
    }
}
