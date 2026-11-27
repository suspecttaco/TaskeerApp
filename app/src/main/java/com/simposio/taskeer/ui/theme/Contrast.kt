package com.simposio.taskeer.ui.theme

import kotlin.math.pow

private const val CHANNEL_MAX = 255.0
private const val LINEAR_THRESHOLD = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_SCALE = 1.055
private const val GAMMA = 2.4
private const val LUMINANCE_OFFSET = 0.05
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722

private fun linear(channel: Int): Double {
    val c = channel / CHANNEL_MAX
    return if (c <= LINEAR_THRESHOLD) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
}

private fun luminance(argb: Int): Double =
    RED_WEIGHT * linear((argb shr 16) and 0xFF) +
        GREEN_WEIGHT * linear((argb shr 8) and 0xFF) +
        BLUE_WEIGHT * linear(argb and 0xFF)

fun contrastRatio(firstArgb: Int, secondArgb: Int): Double {
    val a = luminance(firstArgb)
    val b = luminance(secondArgb)
    return (maxOf(a, b) + LUMINANCE_OFFSET) / (minOf(a, b) + LUMINANCE_OFFSET)
}
