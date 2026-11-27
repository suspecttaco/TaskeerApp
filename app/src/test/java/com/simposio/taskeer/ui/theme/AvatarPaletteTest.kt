package com.simposio.taskeer.ui.theme

import com.simposio.taskeer.domain.auth.AVATAR_PALETTE_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPaletteTest {

    @Test
    fun tamanoIgualAlDelDominio() {
        assertEquals(AVATAR_PALETTE_SIZE, AvatarPalette.colors.size)
    }

    @Test
    fun todosLosParesCumplenContraste45() {
        AvatarPalette.colors.forEachIndexed { index, pair ->
            val ratio = contrastRatio(pair.containerArgb, pair.contentArgb)
            assertTrue("par $index con contraste $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun fondosDistintos() {
        assertEquals(AVATAR_PALETTE_SIZE, AvatarPalette.colors.map { it.containerArgb }.toSet().size)
    }

    @Test
    fun forIndex_fueraDeRango_noLanza() {
        assertEquals(AvatarPalette.colors[1], AvatarPalette.forIndex(AVATAR_PALETTE_SIZE + 1))
        assertEquals(AvatarPalette.colors[AVATAR_PALETTE_SIZE - 1], AvatarPalette.forIndex(-1))
    }
}
