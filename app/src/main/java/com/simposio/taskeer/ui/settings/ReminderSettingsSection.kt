package com.simposio.taskeer.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.ReminderLead
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.tasks.DueTimePickerDialog
import com.simposio.taskeer.ui.tasks.SelectField
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

data class ReminderSettingsActions(
    val onDeadlineEnabledChange: (Boolean) -> Unit,
    val onLeadChange: (ReminderLead) -> Unit,
    val onDigestEnabledChange: (Boolean) -> Unit,
    val onDigestDaysChange: (Set<DayOfWeek>) -> Unit,
    val onDigestTimeChange: (LocalTime) -> Unit,
    val onDefaultTimeChange: (LocalTime) -> Unit,
) {
    companion object {
        val None = ReminderSettingsActions({}, {}, {}, {}, {}, {})
    }
}

private enum class TimeTarget { DIGEST, DEFAULT }

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSettingsSection(
    state: SettingsUiState,
    actions: ReminderSettingsActions,
    permissionNotice: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerTarget by rememberSaveable { mutableStateOf<TimeTarget?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(
            text = stringResource(R.string.settings_reminders_title),
            icon = Icons.Filled.Notifications,
        )

        SwitchRow(
            icon = Icons.Filled.Event,
            label = stringResource(R.string.settings_deadline_switch),
            checked = state.deadline.enabled,
            onCheckedChange = actions.onDeadlineEnabledChange,
        )
        if (state.deadline.enabled) {
            SelectField(
                label = stringResource(R.string.settings_deadline_lead_title),
                options = ReminderLead.entries,
                selected = state.deadline.lead,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onLeadChange,
            )
        }

        SwitchRow(
            icon = Icons.Filled.Summarize,
            label = stringResource(R.string.settings_digest_switch),
            checked = state.digest.enabled,
            onCheckedChange = actions.onDigestEnabledChange,
        )
        if (state.digest.enabled) {
            Text(
                text = stringResource(R.string.settings_digest_days_title),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in state.digest.days
                    FilterChip(
                        selected = selected,
                        onClick = {
                            actions.onDigestDaysChange(
                                if (selected) state.digest.days - day else state.digest.days + day,
                            )
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                    )
                }
            }
            TimeRow(
                label = stringResource(R.string.settings_digest_time_label),
                time = state.digest.time,
                onClick = { pickerTarget = TimeTarget.DIGEST },
            )
        }

        TimeRow(
            label = stringResource(R.string.settings_default_time_label),
            time = state.defaultReminderTime,
            onClick = { pickerTarget = TimeTarget.DEFAULT },
        )

        permissionNotice()
    }

    pickerTarget?.let { target ->
        DueTimePickerDialog(
            initialTime = when (target) {
                TimeTarget.DIGEST -> state.digest.time
                TimeTarget.DEFAULT -> state.defaultReminderTime
            },
            onTimePicked = { time ->
                pickerTarget = null
                when (target) {
                    TimeTarget.DIGEST -> actions.onDigestTimeChange(time)
                    TimeTarget.DEFAULT -> actions.onDefaultTimeChange(time)
                }
            },
            onDismiss = { pickerTarget = null },
        )
    }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        DecorativeIcon(icon = Icons.Filled.Schedule, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(time.format(TIME_FORMATTER)) }
    }
}

@StringRes
private fun ReminderLead.labelRes(): Int = when (this) {
    ReminderLead.AT_DUE -> R.string.reminder_lead_at_due
    ReminderLead.FIFTEEN_MINUTES -> R.string.reminder_lead_fifteen_minutes
    ReminderLead.ONE_HOUR -> R.string.reminder_lead_one_hour
    ReminderLead.ONE_DAY -> R.string.reminder_lead_one_day
}

@Preview(showBackground = true)
@Composable
private fun ReminderSettingsSectionPreview() {
    TaskeerTheme {
        ReminderSettingsSection(
            state = SettingsUiState(),
            actions = ReminderSettingsActions.None,
            permissionNotice = {},
        )
    }
}
