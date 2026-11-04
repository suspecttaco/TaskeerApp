package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun RemindersSection(
    state: RemindersUiState,
    actions: ReminderActions,
    modifier: Modifier = Modifier,
    permissionNotice: @Composable () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.reminder_title),
            style = MaterialTheme.typography.labelLarge,
        )
        if (state.reminderAtLocal != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.reminderAtLocal.format(DUE_DATE_TIME_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = actions.onCancel) {
                    Text(stringResource(R.string.reminder_cancel))
                }
            }
        } else {
            Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text(stringResource(R.string.reminder_add))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminder_later_today)) },
                        onClick = {
                            menuExpanded = false
                            actions.onLaterToday()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminder_tomorrow)) },
                        onClick = {
                            menuExpanded = false
                            actions.onTomorrow()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminder_next_week)) },
                        onClick = {
                            menuExpanded = false
                            actions.onNextWeek()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminder_custom)) },
                        onClick = {
                            menuExpanded = false
                            showDatePicker = true
                        },
                    )
                }
            }
        }
        permissionNotice()
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDate = null,
            onDatePicked = { date ->
                pickedEpochDay = date.toEpochDay()
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        val date = pickedEpochDay?.let(LocalDate::ofEpochDay)
        DueTimePickerDialog(
            initialTime = DEFAULT_REMINDER_TIME,
            onTimePicked = { time ->
                showTimePicker = false
                date?.let { actions.onCustom(LocalDateTime.of(it, time)) }
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RemindersSectionPreview() {
    TaskeerTheme {
        RemindersSection(
            state = RemindersUiState(),
            actions = ReminderActions.None,
        )
    }
}
