package com.simposio.taskeer.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.RepeatUnit
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.ui.theme.Spacing
import java.time.DayOfWeek

@Composable
internal fun RepeatCustomFields(
    state: TaskEditorUiState,
    actions: RepeatActions,
    modifier: Modifier = Modifier,
) {
    val rule = state.repeat ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.repeatIntervalText,
                onValueChange = actions.onIntervalChange,
                label = { Text(stringResource(R.string.task_repeat_interval_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SelectField(
                label = stringResource(R.string.task_repeat_unit_label),
                options = RepeatUnit.entries,
                selected = rule.unit,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onUnitChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (rule.unit == RepeatUnit.WEEK) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val description = stringResource(day.descriptionRes())
                    FilterChip(
                        selected = day in rule.weekdays,
                        onClick = { actions.onWeekdayToggle(day) },
                        label = { Text(stringResource(day.labelRes())) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

@StringRes
internal fun RepeatPreset.labelRes(): Int = when (this) {
    RepeatPreset.NONE -> R.string.task_repeat_none
    RepeatPreset.DAILY -> R.string.task_repeat_daily
    RepeatPreset.WEEKDAYS -> R.string.task_repeat_weekdays
    RepeatPreset.WEEKLY -> R.string.task_repeat_weekly
    RepeatPreset.MONTHLY -> R.string.task_repeat_monthly
    RepeatPreset.YEARLY -> R.string.task_repeat_yearly
    RepeatPreset.CUSTOM -> R.string.task_repeat_custom
}

@StringRes
private fun RepeatUnit.labelRes(): Int = when (this) {
    RepeatUnit.DAY -> R.string.task_repeat_unit_day
    RepeatUnit.WEEK -> R.string.task_repeat_unit_week
    RepeatUnit.MONTH -> R.string.task_repeat_unit_month
    RepeatUnit.YEAR -> R.string.task_repeat_unit_year
}

@StringRes
private fun DayOfWeek.labelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.task_repeat_day_monday
    DayOfWeek.TUESDAY -> R.string.task_repeat_day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.task_repeat_day_wednesday
    DayOfWeek.THURSDAY -> R.string.task_repeat_day_thursday
    DayOfWeek.FRIDAY -> R.string.task_repeat_day_friday
    DayOfWeek.SATURDAY -> R.string.task_repeat_day_saturday
    DayOfWeek.SUNDAY -> R.string.task_repeat_day_sunday
}

@StringRes
private fun DayOfWeek.descriptionRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.task_repeat_day_monday_description
    DayOfWeek.TUESDAY -> R.string.task_repeat_day_tuesday_description
    DayOfWeek.WEDNESDAY -> R.string.task_repeat_day_wednesday_description
    DayOfWeek.THURSDAY -> R.string.task_repeat_day_thursday_description
    DayOfWeek.FRIDAY -> R.string.task_repeat_day_friday_description
    DayOfWeek.SATURDAY -> R.string.task_repeat_day_saturday_description
    DayOfWeek.SUNDAY -> R.string.task_repeat_day_sunday_description
}
