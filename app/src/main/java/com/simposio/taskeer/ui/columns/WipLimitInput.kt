package com.simposio.taskeer.ui.columns

sealed interface WipLimitInput {
    data object Empty : WipLimitInput
    data class Value(val limit: Int) : WipLimitInput
    data object Invalid : WipLimitInput
}

fun parseWipLimitInput(text: String): WipLimitInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return WipLimitInput.Empty
    return trimmed.toIntOrNull()?.let { WipLimitInput.Value(it) } ?: WipLimitInput.Invalid
}
