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
fun rememberTaskEditorHost(
    viewModelFactory: ViewModelProvider.Factory,
    onDelete: (String) -> Unit,
): TaskEditorHost {
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

    if (editorState.isOpen) {
        val savedTask = editorState.taskId != null
        TaskEditorSheet(
            state = editorState,
            actions = TaskEditorActions(
                onTitleChange = editorViewModel::onTitleChange,
                onDescriptionChange = editorViewModel::onDescriptionChange,
                onPriorityChange = editorViewModel::onPriorityChange,
                onAssigneeChange = editorViewModel::onAssigneeChange,
                onToggleCompleted = editorViewModel::onToggleCompleted,
                onToggleMyDay = editorViewModel::onToggleMyDay,
                onOpenPanel = editorViewModel::openPanel,
                onListChosen = editorViewModel::onListChosen,
                onPickerDate = editorViewModel::onPickerDate,
                onPickerTime = { time ->
                    editorViewModel.onPickerTime(time)?.let { picked ->
                        when (picked.target) {
                            PickerTarget.REMINDER -> remindersViewModel.custom(picked.local)
                            PickerTarget.SUBTASK_DUE -> subtasksViewModel.onEditDueAtChange(picked.local)
                            PickerTarget.TASK_DUE -> editorViewModel.onDueAtChange(picked.local)
                        }
                    }
                },
                onClearDue = editorViewModel::onClearDue,
                onBack = editorViewModel::onBack,
                onRequestClose = editorViewModel::requestClose,
                onConfirmDiscard = editorViewModel::confirmDiscard,
                onKeepEditing = editorViewModel::keepEditing,
                onSave = editorViewModel::save,
                onDelete = {
                    editorState.taskId?.let(onDelete)
                    editorViewModel.close()
                },
                onClosed = editorViewModel::dismiss,
            ),
            repeatActions = RepeatActions(
                onPresetChange = editorViewModel::onRepeatPresetChange,
                onIntervalChange = editorViewModel::onRepeatIntervalChange,
                onUnitChange = editorViewModel::onRepeatUnitChange,
                onWeekdayToggle = editorViewModel::onRepeatWeekdayToggle,
            ),
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
            reminders = if (savedTask) {
                remindersState
            } else {
                RemindersUiState(
                    reminderAtLocal = editorState.reminderDraftLocal,
                    error = editorState.reminderError,
                )
            },
            reminderActions = if (savedTask) {
                ReminderActions(
                    onLaterToday = remindersViewModel::laterToday,
                    onTomorrow = remindersViewModel::tomorrow,
                    onNextWeek = remindersViewModel::nextWeek,
                    onCustom = remindersViewModel::custom,
                    onCancel = remindersViewModel::cancel,
                )
            } else {
                ReminderActions(
                    onLaterToday = editorViewModel::reminderLaterToday,
                    onTomorrow = editorViewModel::reminderTomorrow,
                    onNextWeek = editorViewModel::reminderNextWeek,
                    onCustom = editorViewModel::reminderCustom,
                    onCancel = editorViewModel::clearReminderDraft,
                )
            },
            permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
        )
    }

    return remember(editorViewModel) {
        TaskEditorHost(
            openForEdit = editorViewModel::openForEdit,
            openForCreate = editorViewModel::openForCreate,
        )
    }
}
