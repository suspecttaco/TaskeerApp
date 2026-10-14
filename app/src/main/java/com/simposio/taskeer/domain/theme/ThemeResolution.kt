package com.simposio.taskeer.domain.theme

import com.simposio.taskeer.domain.model.ThemeMode

fun ThemeMode.resolveIsDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO -> systemDark
}
