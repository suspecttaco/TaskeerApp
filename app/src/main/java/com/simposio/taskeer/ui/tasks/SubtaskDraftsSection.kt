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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.EmptyHint
import com.simposio.taskeer.ui.components.InlineAddField
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorRowEnter
import com.simposio.taskeer.ui.navigation.editorRowExit
import com.simposio.taskeer.ui.navigation.editorSizeSpec
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun SubtaskDraftsSection(
    state: TaskEditorUiState,
    actions: SubtaskDraftActions,
    onPickSubtaskDue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    val initialIds = remember { state.subtaskDrafts.map { it.id }.toSet() }
    Column(
        modifier = modifier.animateContentSize(editorSizeSpec(animated)),
        verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        SectionHeader(
            text = stringResource(R.string.subtasks_title),
            icon = Icons.Filled.Checklist,
        )
        AnimatedVisibility(
            visible = state.subtaskDrafts.isEmpty(),
            enter = editorRowEnter(animated),
            exit = editorRowExit(animated),
        ) {
            EmptyHint(text = stringResource(R.string.subtasks_empty))
        }
        state.subtaskDrafts.forEach { draft ->
            key(draft.id) {
                AnimatedSubtaskRow(
                    id = draft.id,
                    isEditing = state.editingDraftId == draft.id,
                    initiallyVisible = draft.id in initialIds,
                    onExited = { actions.onDelete(draft.id) },
                ) { editing, requestDelete ->
                    if (editing) {
                        SubtaskEditFields(
                            title = state.editingDraftTitle,
                            dueAtLocal = state.editingDraftDueAtLocal,
                            assigneeId = state.editingDraftAssigneeId,
                            onTitleChange = actions.onEditTitleChange,
                            onDueAtChange = actions.onEditDueAtChange,
                            onPickDueAt = onPickSubtaskDue,
                            onAssigneeChange = actions.onEditAssigneeChange,
                            onSave = actions.onSaveEdit,
                            onCancel = actions.onCancelEdit,
                        )
                    } else {
                        SubtaskDraftRow(draft, actions.onStartEdit) { requestDelete() }
                    }
                }
            }
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
            onPickSubtaskDue = {},
        )
    }
}
