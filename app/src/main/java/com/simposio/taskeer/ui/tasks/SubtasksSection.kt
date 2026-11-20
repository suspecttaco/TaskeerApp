package com.simposio.taskeer.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.Subtask
import com.simposio.taskeer.domain.model.SyncMetadata
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorRowEnter
import com.simposio.taskeer.ui.navigation.editorRowExit
import com.simposio.taskeer.ui.navigation.editorSizeSpec
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.Instant
import java.time.LocalDateTime

@Composable
fun SubtasksSection(
    state: SubtasksUiState,
    onNewTitleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onStartEdit: (String) -> Unit,
    onEditTitleChange: (String) -> Unit,
    onEditDueAtChange: (LocalDateTime?) -> Unit,
    onPickSubtaskDue: () -> Unit,
    onEditAssigneeChange: (String?) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    val initialIds = remember { state.items.map { it.id }.toSet() }
    Column(
        modifier = modifier.animateContentSize(editorSizeSpec(animated)),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        SectionHeader(
            text = stringResource(R.string.subtasks_title),
            icon = Icons.Filled.Checklist,
        )
        AnimatedVisibility(
            visible = state.items.isEmpty(),
            enter = editorRowEnter(animated),
            exit = editorRowExit(animated),
        ) {
            Text(
                text = stringResource(R.string.subtasks_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.items.forEach { subtask ->
            key(subtask.id) {
                AnimatedSubtaskRow(
                    id = subtask.id,
                    isEditing = state.editingId == subtask.id,
                    initiallyVisible = subtask.id in initialIds,
                    onExited = { onDelete(subtask.id) },
                ) { editing, requestDelete ->
                    if (editing) {
                        SubtaskEditFields(
                            title = state.editingTitle,
                            dueAtLocal = state.editingDueAtLocal,
                            assigneeId = state.editingAssigneeId,
                            onTitleChange = onEditTitleChange,
                            onDueAtChange = onEditDueAtChange,
                            onPickDueAt = onPickSubtaskDue,
                            onAssigneeChange = onEditAssigneeChange,
                            onSave = onSaveEdit,
                            onCancel = onCancelEdit,
                        )
                    } else {
                        SubtaskRow(subtask, onToggle, onStartEdit) { requestDelete() }
                    }
                }
            }
        }
        InlineAddField(
            value = state.newTitle,
            onValueChange = onNewTitleChange,
            label = stringResource(R.string.subtask_add_label),
            addDescription = stringResource(R.string.subtask_add),
            onAdd = onAdd,
            enabled = state.newTitle.isNotBlank(),
        )
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
        ActionIcon(
            icon = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.subtask_edit),
            onClick = { onStartEdit(subtask.id) },
        )
        ActionIcon(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.subtask_delete),
            onClick = { onDelete(subtask.id) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubtasksSectionPreview() {
    TaskeerTheme {
        SubtasksSection(
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
            onPickSubtaskDue = {},
            onEditAssigneeChange = {},
            onSaveEdit = {},
            onCancelEdit = {},
            onDelete = {},
        )
    }
}

private fun previewSync(id: String) =
    SyncMetadata(id, Instant.EPOCH, pending = false, deletedAt = null)
