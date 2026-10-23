package com.simposio.taskeer.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TaskEditorDialog(
    state: TaskEditorUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueAtChange: (LocalDateTime?) -> Unit,
    onAssigneeChange: (String?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isOpen) {
        return
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (state.taskId == null) {
                            R.string.task_editor_title_create
                        } else {
                            R.string.task_editor_title_edit
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.task_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.task_description_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.task_priority_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                TaskPriority.entries.forEach { priority ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.priority == priority,
                            onClick = { onPriorityChange(priority) },
                        )
                        Text(text = stringResource(priority.labelRes()))
                    }
                }

                Text(
                    text = stringResource(R.string.task_due_at_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(
                            text = state.dueAtLocal?.format(DATE_TIME_FORMATTER)
                                ?: stringResource(R.string.task_due_at_none),
                        )
                    }
                    if (state.dueAtLocal != null) {
                        TextButton(onClick = { onDueAtChange(null) }) {
                            Text(stringResource(R.string.task_due_at_clear))
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.task_assignee_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.assigneeId == null,
                        onClick = { onAssigneeChange(null) },
                    )
                    Text(text = stringResource(R.string.task_assignee_none))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.assigneeId == LocalUser.ID,
                        onClick = { onAssigneeChange(LocalUser.ID) },
                    )
                    Text(text = stringResource(R.string.task_assignee_local))
                }

                state.error?.let { error ->
                    Text(
                        text = error.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.taskId != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.task_delete))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.task_cancel))
                    }
                    Button(onClick = onSave, enabled = state.title.isNotBlank() && !state.isSaving) {
                        Text(stringResource(R.string.task_save))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDate = state.dueAtLocal?.toLocalDate(),
            onDatePicked = { date ->
                pickedDate = date
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        val date = pickedDate
        DueTimePickerDialog(
            initialTime = state.dueAtLocal?.toLocalTime() ?: LocalTime.of(0, 0),
            onTimePicked = { time ->
                showTimePicker = false
                onDueAtChange(date?.let { LocalDateTime.of(it, time) })
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    initialDate: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // Los milisegundos del selector están en UTC: leerlos en la zona del
                    // usuario movería el día al cruzar medianoche.
                    val date = state.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    if (date != null) {
                        onDatePicked(date)
                    }
                },
            ) {
                Text(stringResource(R.string.task_due_at_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.task_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimePickerDialog(
    initialTime: LocalTime,
    onTimePicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.task_due_at_time_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                TimePicker(state = state)
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.task_cancel))
                    }
                    TextButton(
                        onClick = { onTimePicked(LocalTime.of(state.hour, state.minute)) },
                    ) {
                        Text(stringResource(R.string.task_due_at_accept))
                    }
                }
            }
        }
    }
}

@StringRes
private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Preview(showBackground = true)
@Composable
private fun TaskEditorDialogPreview() {
    TaskeerTheme {
        TaskEditorDialog(
            state = TaskEditorUiState(
                isOpen = true,
                columnId = "columna-1",
                title = "Escribir el informe",
                description = "Con los datos del trimestre",
                priority = TaskPriority.HIGH,
                dueAtLocal = LocalDateTime.of(2026, 10, 22, 12, 30),
                assigneeId = LocalUser.ID,
            ),
            onTitleChange = {},
            onDescriptionChange = {},
            onPriorityChange = {},
            onDueAtChange = {},
            onAssigneeChange = {},
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
