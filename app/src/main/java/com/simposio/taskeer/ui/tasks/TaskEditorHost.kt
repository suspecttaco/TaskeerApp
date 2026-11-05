package com.simposio.taskeer.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice

class TaskEditorHost internal constructor(
    val openForEdit: (String) -> Unit,
    val openForCreate: (String) -> Unit,
)

@Composable
fun rememberTaskEditorHost(viewModelFactory: ViewModelProvider.Factory): TaskEditorHost {
    val editorViewModel: TaskEditorViewModel = viewModel(factory = viewModelFactory)
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()

    val subtasksViewModel: SubtasksViewModel = viewModel(factory = viewModelFactory)
    val subtasksState by subtasksViewModel.uiState.collectAsStateWithLifecycle()

    val remindersViewModel: RemindersViewModel = viewModel(factory = viewModelFactory)
    val remindersState by remindersViewModel.uiState.collectAsStateWithLifecycle()

    val openTaskId = editorState.taskId.takeIf { editorState.isOpen }
    LaunchedEffect(openTaskId) {
        if (openTaskId == null) {
            subtasksViewModel.close()
            remindersViewModel.close()
        } else {
            subtasksViewModel.open(openTaskId)
            remindersViewModel.open(openTaskId)
        }
    }

    TaskEditorDialog(
        state = editorState,
        onTitleChange = editorViewModel::onTitleChange,
        onDescriptionChange = editorViewModel::onDescriptionChange,
        onPriorityChange = editorViewModel::onPriorityChange,
        onDueAtChange = editorViewModel::onDueAtChange,
        onAssigneeChange = editorViewModel::onAssigneeChange,
        subtasks = subtasksState,
        subtaskActions = SubtaskActions(
            onNewTitleChange = subtasksViewModel::onNewTitleChange,
            onAdd = subtasksViewModel::add,
            onToggle = subtasksViewModel::toggle,
            onStartEdit = subtasksViewModel::startEdit,
            onEditTitleChange = subtasksViewModel::onEditTitleChange,
            onEditDueAtChange = subtasksViewModel::onEditDueAtChange,
            onEditAssigneeChange = subtasksViewModel::onEditAssigneeChange,
            onSaveEdit = subtasksViewModel::saveEdit,
            onCancelEdit = subtasksViewModel::cancelEdit,
            onDelete = subtasksViewModel::delete,
        ),
        subtaskDraftActions = SubtaskDraftActions(
            onNewTitleChange = editorViewModel::onNewSubtaskTitleChange,
            onAdd = editorViewModel::addSubtaskDraft,
            onStartEdit = editorViewModel::startEditSubtaskDraft,
            onEditTitleChange = editorViewModel::onEditSubtaskTitleChange,
            onEditDueAtChange = editorViewModel::onEditSubtaskDueAtChange,
            onEditAssigneeChange = editorViewModel::onEditSubtaskAssigneeChange,
            onSaveEdit = editorViewModel::saveSubtaskDraftEdit,
            onCancelEdit = editorViewModel::cancelSubtaskDraftEdit,
            onDelete = editorViewModel::removeSubtaskDraft,
        ),
        reminders = if (editorState.taskId == null) {
            RemindersUiState(
                reminderAtLocal = editorState.reminderDraftLocal,
                error = editorState.reminderError,
            )
        } else {
            remindersState
        },
        reminderActions = if (editorState.taskId == null) {
            ReminderActions(
                onLaterToday = editorViewModel::reminderLaterToday,
                onTomorrow = editorViewModel::reminderTomorrow,
                onNextWeek = editorViewModel::reminderNextWeek,
                onCustom = editorViewModel::reminderCustom,
                onCancel = editorViewModel::clearReminderDraft,
            )
        } else {
            ReminderActions(
                onLaterToday = remindersViewModel::laterToday,
                onTomorrow = remindersViewModel::tomorrow,
                onNextWeek = remindersViewModel::nextWeek,
                onCustom = remindersViewModel::custom,
                onCancel = remindersViewModel::cancel,
            )
        },
        permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
        onSave = editorViewModel::save,
        onDelete = editorViewModel::delete,
        onDismiss = editorViewModel::dismiss,
    )

    return remember(editorViewModel) {
        TaskEditorHost(
            openForEdit = editorViewModel::openForEdit,
            openForCreate = editorViewModel::openForCreate,
        )
    }
}
