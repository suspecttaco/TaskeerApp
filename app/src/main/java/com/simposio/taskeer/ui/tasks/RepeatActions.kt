package com.simposio.taskeer.ui.tasks

import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.task.RepeatPreset
import java.time.DayOfWeek

data class RepeatActions(
    val onPresetChange: (RepeatPreset) -> Unit,
    val onIntervalChange: (String) -> Unit,
    val onUnitChange: (RepeatUnit) -> Unit,
    val onWeekdayToggle: (DayOfWeek) -> Unit,
) {
    companion object {
        val None = RepeatActions({}, {}, {}, {})
    }
}
