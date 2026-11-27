package com.simposio.taskeer.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTokensTest {

    @Test
    fun elevaciones_equivalenALosLiteralesRetirados() {
        assertEquals(1.dp, Elevation.Resting)
        assertEquals(8.dp, Elevation.Dragged)
        assertEquals(6.dp, Elevation.Floating)
        assertEquals(6.dp, Elevation.Picker)
    }

    @Test
    fun medidasDeTablero_equivalenALosLiteralesRetirados() {
        assertEquals(280.dp, Spacing.BoardColumnWidth)
        assertEquals(256.dp, Spacing.BoardDragCardWidth)
        assertEquals(1.dp, Spacing.CardBorder)
        assertEquals(2.dp, Spacing.SpinnerStroke)
        assertEquals(1.5f, Spacing.StrikeStrokeDp)
    }

    @Test
    fun alfas_equivalenALosLiteralesRetirados() {
        assertEquals(0.38f, Emphasis.Disabled)
        assertEquals(0.5f, Emphasis.OutOfMonth)
    }
}
