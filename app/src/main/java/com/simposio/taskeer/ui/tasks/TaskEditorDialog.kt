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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TaskEditorDialog(
    state: TaskEditorUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueAtChange: (LocalDateTime?) -> Unit,
    onAssigneeChange: (String?) -> Unit,
    subtasks: SubtasksUiState,
    subtaskActions: SubtaskActions,
    reminders: RemindersUiState,
    reminderActions: ReminderActions,
    permissionNotice: @Composable () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isOpen) {
        return
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.ScreenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
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

                AppDivider()
                val priorityDescription = stringResource(
                    R.string.task_priority_description,
                    stringResource(state.priority.labelRes()),
                )
                SelectField(
                    label = stringResource(R.string.task_priority_label),
                    options = TaskPriority.entries,
                    selected = state.priority,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = onPriorityChange,
                    leadingIcon = Icons.Filled.Flag,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = priorityDescription
                    },
                )

                AppDivider()
                SectionHeader(
                    text = stringResource(R.string.task_due_at_label),
                    icon = Icons.Filled.Schedule,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(
                            text = state.dueAtLocal?.format(DUE_DATE_TIME_FORMATTER)
                                ?: stringResource(R.string.task_due_at_none),
                        )
                    }
                    if (state.dueAtLocal != null) {
                        ActionIcon(
                            icon = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.task_due_at_clear),
                            onClick = { onDueAtChange(null) },
                        )
                    }
                }

                AppDivider()
                SectionHeader(
                    text = stringResource(R.string.task_assignee_label),
                    icon = Icons.Filled.Person,
                )
                RadioOption(
                    label = stringResource(R.string.task_assignee_me),
                    selected = state.assigneeId == LocalUser.ID,
                    onSelect = { onAssigneeChange(LocalUser.ID) },
                )

                AppDivider()
                SubtasksSection(
                    taskExists = state.taskId != null,
                    state = subtasks,
                    onNewTitleChange = subtaskActions.onNewTitleChange,
                    onAdd = subtaskActions.onAdd,
                    onToggle = subtaskActions.onToggle,
                    onStartEdit = subtaskActions.onStartEdit,
                    onEditTitleChange = subtaskActions.onEditTitleChange,
                    onEditDueAtChange = subtaskActions.onEditDueAtChange,
                    onEditAssigneeChange = subtaskActions.onEditAssigneeChange,
                    onSaveEdit = subtaskActions.onSaveEdit,
                    onCancelEdit = subtaskActions.onCancelEdit,
                    onDelete = subtaskActions.onDelete,
                )

                if (state.taskId != null) {
                    AppDivider()
                    RemindersSection(
                        state = reminders,
                        actions = reminderActions,
                        permissionNotice = permissionNotice,
                    )
                }

                state.error?.let { error ->
                    Text(
                        text = error.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AppDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.taskId != null) {
                        TextButton(onClick = onDelete, enabled = !state.isSaving) {
                            Text(stringResource(R.string.task_delete))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.task_cancel))
                    }
                    Button(
                        onClick = onSave,
                        enabled = state.title.isNotBlank() && !state.isSaving,
                    ) {
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
            initialTime = state.dueAtLocal?.toLocalTime() ?: LocalTime.of(0, 0),
            onTimePicked = { time ->
                showTimePicker = false
                onDueAtChange(date?.let { LocalDateTime.of(it, time) })
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@StringRes
private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}

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
            subtasks = SubtasksUiState(),
            subtaskActions = SubtaskActions.None,
            reminders = RemindersUiState(),
            reminderActions = ReminderActions.None,
            permissionNotice = {},
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
