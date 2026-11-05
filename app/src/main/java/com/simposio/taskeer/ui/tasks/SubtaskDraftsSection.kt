package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun SubtaskDraftsSection(
    state: TaskEditorUiState,
    actions: SubtaskDraftActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(
            text = stringResource(R.string.subtasks_title),
            icon = Icons.Filled.Checklist,
        )
        if (state.subtaskDrafts.isEmpty()) {
            Text(
                text = stringResource(R.string.subtasks_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.subtaskDrafts.forEach { draft ->
            if (state.editingDraftId == draft.id) {
                SubtaskEditFields(
                    title = state.editingDraftTitle,
                    dueAtLocal = state.editingDraftDueAtLocal,
                    assigneeId = state.editingDraftAssigneeId,
                    onTitleChange = actions.onEditTitleChange,
                    onDueAtChange = actions.onEditDueAtChange,
                    onAssigneeChange = actions.onEditAssigneeChange,
                    onSave = actions.onSaveEdit,
                    onCancel = actions.onCancelEdit,
                )
            } else {
                SubtaskDraftRow(draft, actions.onStartEdit, actions.onDelete)
            }
            AppDivider()
        }
        InlineAddField(
            value = state.newSubtaskTitle,
            onValueChange = actions.onNewTitleChange,
            label = stringResource(R.string.subtask_add_label),
            addDescription = stringResource(R.string.subtask_add),
            onAdd = actions.onAdd,
            enabled = state.newSubtaskTitle.isNotBlank(),
        )
    }
}

@Composable
private fun SubtaskDraftRow(
    draft: SubtaskDraftItem,
    onStartEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = draft.title, modifier = Modifier.weight(1f))
        ActionIcon(
            icon = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.subtask_edit),
            onClick = { onStartEdit(draft.id) },
        )
        ActionIcon(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.subtask_delete),
            onClick = { onDelete(draft.id) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubtaskDraftsSectionPreview() {
    TaskeerTheme {
        SubtaskDraftsSection(
            state = TaskEditorUiState(
                isOpen = true,
                subtaskDrafts = listOf(
                    SubtaskDraftItem("d1", "Reunir los datos"),
                    SubtaskDraftItem("d2", "Redactar el borrador"),
                ),
            ),
            actions = SubtaskDraftActions.None,
        )
    }
}
