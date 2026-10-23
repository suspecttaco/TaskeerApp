package com.simposio.taskeer.domain.theme

enum class ColorOrigin {
    BRAND,
    DYNAMIC,
}

// El color dinámico (Material You) existe desde Android 12 / API 31.
private const val DYNAMIC_COLOR_MIN_SDK: Int = 31

fun isDynamicColorAvailable(sdkInt: Int): Boolean = sdkInt >= DYNAMIC_COLOR_MIN_SDK

fun resolveColorOrigin(dynamicColorEnabled: Boolean, sdkInt: Int): ColorOrigin =
    if (dynamicColorEnabled && isDynamicColorAvailable(sdkInt)) {
        ColorOrigin.DYNAMIC
    } else {
        ColorOrigin.BRAND
    }
