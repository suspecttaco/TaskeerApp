package com.simposio.taskeer.ui.theme

import com.simposio.taskeer.domain.auth.AVATAR_PALETTE_SIZE

data class AvatarColors(val containerArgb: Int, val contentArgb: Int)

// Mismos tonos en claro y oscuro: la identidad no debe cambiar con el tema ni con el color dinámico.
object AvatarPalette {
    private const val WHITE = 0xFFFFFFFF.toInt()

    val colors: List<AvatarColors> = listOf(
        AvatarColors(0xFF1565C0.toInt(), WHITE),
        AvatarColors(0xFF2E7D32.toInt(), WHITE),
        AvatarColors(0xFF8D5A00.toInt(), WHITE),
        AvatarColors(0xFFC62828.toInt(), WHITE),
        AvatarColors(0xFF6A1B9A.toInt(), WHITE),
        AvatarColors(0xFF00695C.toInt(), WHITE),
        AvatarColors(0xFFAD1457.toInt(), WHITE),
        AvatarColors(0xFF455A64.toInt(), WHITE),
    ).also { check(it.size == AVATAR_PALETTE_SIZE) }

    fun forIndex(index: Int): AvatarColors = colors[Math.floorMod(index, colors.size)]
}
