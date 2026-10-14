package com.simposio.taskeer.domain.model

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
