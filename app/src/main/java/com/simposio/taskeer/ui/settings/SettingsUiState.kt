package com.simposio.taskeer.ui.settings

import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.domain.model.DeadlineReminderSettings
import com.simposio.taskeer.domain.model.DigestSettings
import com.simposio.taskeer.domain.model.ThemeMode
import java.time.LocalTime

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColor: Boolean = true,
    val deadline: DeadlineReminderSettings = DeadlineReminderSettings(),
    val digest: DigestSettings = DigestSettings(),
    val defaultReminderTime: LocalTime = DEFAULT_REMINDER_TIME,
)
