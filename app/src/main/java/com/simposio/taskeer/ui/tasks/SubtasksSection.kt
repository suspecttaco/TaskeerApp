package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun SubtasksSection(
    taskExists: Boolean,
    state: SubtasksUiState,
    onNewTitleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onStartEdit: (String) -> Unit,
    onEditTitleChange: (String) -> Unit,
    onEditDueAtChange: (LocalDateTime?) -> Unit,
    onEditAssigneeChange: (String?) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.subtasks_title),
            style = MaterialTheme.typography.labelLarge,
        )
        if (!taskExists) {
            Text(
                text = stringResource(R.string.subtasks_create_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        if (state.items.isEmpty()) {
            Text(
                text = stringResource(R.string.subtasks_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.items.forEach { subtask ->
            if (state.editingId == subtask.id) {
                SubtaskEditRow(
                    state,
                    onEditTitleChange,
                    onEditDueAtChange,
                    onEditAssigneeChange,
                    onSaveEdit,
                    onCancelEdit,
                )
            } else {
                SubtaskRow(subtask, onToggle, onStartEdit, onDelete)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.newTitle,
                onValueChange = onNewTitleChange,
                label = { Text(stringResource(R.string.subtask_add_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd, enabled = state.newTitle.isNotBlank()) {
                Text(stringResource(R.string.subtask_add))
            }
        }
        state.error?.let { error ->
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: Subtask,
    onToggle: (String, Boolean) -> Unit,
    onStartEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = subtask.completed,
            onCheckedChange = { onToggle(subtask.id, it) },
        )
        Text(text = subtask.title, modifier = Modifier.weight(1f))
        TextButton(onClick = { onStartEdit(subtask.id) }) {
            Text(stringResource(R.string.subtask_edit))
        }
        TextButton(onClick = { onDelete(subtask.id) }) {
            Text(stringResource(R.string.subtask_delete))
        }
    }
}

@Composable
private fun SubtaskEditRow(
    state: SubtasksUiState,
    onEditTitleChange: (String) -> Unit,
    onEditDueAtChange: (LocalDateTime?) -> Unit,
    onEditAssigneeChange: (String?) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.editingTitle,
            onValueChange = onEditTitleChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.subtask_due_at_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(
                    text = state.editingDueAtLocal?.format(DUE_DATE_TIME_FORMATTER)
                        ?: stringResource(R.string.task_due_at_none),
                )
            }
            if (state.editingDueAtLocal != null) {
                TextButton(onClick = { onEditDueAtChange(null) }) {
                    Text(stringResource(R.string.task_due_at_clear))
                }
            }
        }
        Text(
            text = stringResource(R.string.subtask_assignee_label),
            style = MaterialTheme.typography.labelLarge,
        )
        RadioOption(
            label = stringResource(R.string.task_assignee_none),
            selected = state.editingAssigneeId == null,
            onSelect = { onEditAssigneeChange(null) },
        )
        RadioOption(
            label = stringResource(R.string.task_assignee_local),
            selected = state.editingAssigneeId == LocalUser.ID,
            onSelect = { onEditAssigneeChange(LocalUser.ID) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancelEdit) {
                Text(stringResource(R.string.subtask_cancel))
            }
            TextButton(onClick = onSaveEdit, enabled = state.editingTitle.isNotBlank()) {
                Text(stringResource(R.string.subtask_save))
            }
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDate = state.editingDueAtLocal?.toLocalDate(),
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
            initialTime = state.editingDueAtLocal?.toLocalTime() ?: LocalTime.of(0, 0),
            onTimePicked = { time ->
                showTimePicker = false
                onEditDueAtChange(date?.let { LocalDateTime.of(it, time) })
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubtasksSectionPreview() {
    TaskeerTheme {
        SubtasksSection(
            taskExists = true,
            state = SubtasksUiState(
                taskId = "tarea-1",
                items = listOf(
                    Subtask("tarea-1", "Reunir los datos", true, 0, createdAt = Instant.EPOCH, sync = previewSync("s1")),
                    Subtask("tarea-1", "Redactar el borrador", false, 1, createdAt = Instant.EPOCH, sync = previewSync("s2")),
                ),
            ),
            onNewTitleChange = {},
            onAdd = {},
            onToggle = { _, _ -> },
            onStartEdit = {},
            onEditTitleChange = {},
            onEditDueAtChange = {},
            onEditAssigneeChange = {},
            onSaveEdit = {},
            onCancelEdit = {},
            onDelete = {},
        )
    }
}

private fun previewSync(id: String) =
    SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)
