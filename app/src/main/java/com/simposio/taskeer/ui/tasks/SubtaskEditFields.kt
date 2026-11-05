package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun SubtaskEditFields(
    title: String,
    dueAtLocal: LocalDateTime?,
    assigneeId: String?,
    onTitleChange: (String) -> Unit,
    onDueAtChange: (LocalDateTime?) -> Unit,
    onAssigneeChange: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
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
                    text = dueAtLocal?.format(DUE_DATE_TIME_FORMATTER)
                        ?: stringResource(R.string.task_due_at_none),
                )
            }
            if (dueAtLocal != null) {
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.task_due_at_clear),
                    onClick = { onDueAtChange(null) },
                )
            }
        }
        Text(
            text = stringResource(R.string.subtask_assignee_label),
            style = MaterialTheme.typography.labelLarge,
        )
        RadioOption(
            label = stringResource(R.string.task_assignee_none),
            selected = assigneeId == null,
            onSelect = { onAssigneeChange(null) },
        )
        RadioOption(
            label = stringResource(R.string.task_assignee_local),
            selected = assigneeId == LocalUser.ID,
            onSelect = { onAssigneeChange(LocalUser.ID) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            ActionIcon(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.subtask_cancel),
                onClick = onCancel,
            )
            ActionIcon(
                icon = Icons.Filled.Check,
                contentDescription = stringResource(R.string.subtask_save),
                onClick = onSave,
                enabled = title.isNotBlank(),
            )
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDate = dueAtLocal?.toLocalDate(),
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
            initialTime = dueAtLocal?.toLocalTime() ?: LocalTime.of(0, 0),
            onTimePicked = { time ->
                showTimePicker = false
                onDueAtChange(date?.let { LocalDateTime.of(it, time) })
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
