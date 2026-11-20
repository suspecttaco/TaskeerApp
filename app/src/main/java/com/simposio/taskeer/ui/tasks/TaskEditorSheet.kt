package com.simposio.taskeer.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.DEFAULT_REMINDER_TIME
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorPanelTransition
import com.simposio.taskeer.ui.navigation.panelEnter
import com.simposio.taskeer.ui.navigation.panelExit
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalTime
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    state: TaskEditorUiState,
    actions: TaskEditorActions,
    subtasks: SubtasksUiState,
    subtaskActions: SubtaskActions,
    subtaskDraftActions: SubtaskDraftActions,
    reminders: RemindersUiState,
    reminderActions: ReminderActions,
    repeatActions: RepeatActions,
    permissionNotice: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canDismiss = rememberUpdatedState(!state.isDirty || state.isClosing)
    val requestClose = rememberUpdatedState(actions.onRequestClose)
    val onClosed = rememberUpdatedState(actions.onClosed)
    // Arrastre, velo y cierre programático pasan por la misma regla: con cambios sin guardar solo se cierra tras confirmar.
    val confirmValueChange = remember {
        { value: SheetValue ->
            if (value != SheetValue.Hidden || canDismiss.value) {
                true
            } else {
                requestClose.value()
                false
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmValueChange,
    )

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pillFocus = remember { PillFocus() }
    val titleFocus = remember { FocusRequester() }
    val formScroll = rememberScrollState()
    var lastPanel by remember { mutableStateOf<EditorPanel>(EditorPanel.None) }
    // El formulario entra con retardo dentro del AnimatedContent: el foco solo se pide cuando ya está compuesto.
    val composedForms = remember { mutableIntStateOf(0) }
    val awaitForm = suspend { snapshotFlow { composedForms.intValue }.first { it > 0 } }
    val animated = rememberAnimationsEnabled()
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val contentHeight = (windowHeight * Spacing.EditorSheetHeightFraction - Spacing.EditorDragHandleHeight)
        .coerceAtLeast(Spacing.EditorSheetMinHeight)

    LaunchedEffect(Unit) {
        if (state.taskId == null && state.panel == EditorPanel.None) {
            awaitForm()
            titleFocus.requestFocusIfAttached()
            keyboard?.show()
        }
    }

    LaunchedEffect(state.panel) {
        if (state.panel == EditorPanel.None) {
            awaitForm()
            pillFocus.forPanel(lastPanel)?.requestFocusIfAttached()
        } else {
            lastPanel = state.panel
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    LaunchedEffect(state.isClosing) {
        if (state.isClosing) {
            focusManager.clearFocus()
            sheetState.hide()
            onClosed.value()
        }
    }

    val title = stringResource(
        if (state.taskId == null) R.string.task_editor_title_create else R.string.task_editor_title_edit,
    )
    val dragDescription = stringResource(R.string.quick_add_drag_handle)

    ModalBottomSheet(
        onDismissRequest = actions.onClosed,
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
        // Atrás lo resuelve el BackHandler de abajo, paso a paso; el de la hoja la cerraría de golpe.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(enabled = true) { actions.onBack() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .height(contentHeight)
                .padding(horizontal = Spacing.ScreenPadding)
                .semantics { paneTitle = title },
        ) {
            AnimatedContent(
                targetState = state.panel,
                transitionSpec = { editorPanelTransition(animated) },
                contentAlignment = Alignment.TopStart,
                label = "editorPanel",
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { panel ->
                Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                    when (panel) {
                        EditorPanel.None -> {
                            DisposableEffect(Unit) {
                                composedForms.intValue++
                                onDispose { composedForms.intValue-- }
                            }
                            EditorForm(
                                state = state,
                                actions = actions,
                                subtasks = subtasks,
                                subtaskActions = subtaskActions,
                                subtaskDraftActions = subtaskDraftActions,
                                reminders = reminders,
                                reminderActions = reminderActions,
                                repeatActions = repeatActions,
                                permissionNotice = permissionNotice,
                                pillFocus = pillFocus,
                                titleFocus = titleFocus,
                                scroll = formScroll,
                            )
                        }
                        is EditorPanel.DateTime -> {
                            val due = when (panel.target) {
                                PickerTarget.TASK_DUE -> state.dueAtLocal
                                PickerTarget.REMINDER -> null
                                PickerTarget.SUBTASK_DUE ->
                                    if (state.taskId == null) state.editingDraftDueAtLocal else subtasks.editingDueAtLocal
                            }
                            val defaultTime = if (panel.target == PickerTarget.REMINDER) {
                                DEFAULT_REMINDER_TIME
                            } else {
                                LocalTime.MIDNIGHT
                            }
                            DateTimePanel(
                                target = panel.target,
                                pickedEpochDay = panel.pickedEpochDay,
                                initialDate = due?.toLocalDate(),
                                initialTime = due?.toLocalTime() ?: defaultTime,
                                onPickDate = actions.onPickerDate,
                                onPickTime = actions.onPickerTime,
                                onClear = actions.onClearDue,
                                onBack = actions.onBack,
                            )
                        }
                        EditorPanel.ListPicker -> ListPanel(state, actions.onListChosen, onBack = actions.onBack)
                        EditorPanel.DiscardConfirm -> DiscardPanel(
                            onDiscard = actions.onConfirmDiscard,
                            onKeepEditing = actions.onKeepEditing,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorForm(
    state: TaskEditorUiState,
    actions: TaskEditorActions,
    subtasks: SubtasksUiState,
    subtaskActions: SubtaskActions,
    subtaskDraftActions: SubtaskDraftActions,
    reminders: RemindersUiState,
    reminderActions: ReminderActions,
    repeatActions: RepeatActions,
    permissionNotice: @Composable () -> Unit,
    pillFocus: PillFocus,
    titleFocus: FocusRequester,
    scroll: ScrollState,
) {
    val animated = rememberAnimationsEnabled()
    EditorHeader(state = state, actions = actions)
    val shownError = rememberLastNonNull(state.error?.message)
    AnimatedVisibility(
        visible = state.error != null,
        enter = panelEnter(animated),
        exit = panelExit(animated),
    ) {
        Text(
            text = shownError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.InlineGap),
        )
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        EditorRoute(state = state, onPickList = { actions.onOpenPanel(EditorPanel.ListPicker) }, focus = pillFocus)

        TextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            textStyle = MaterialTheme.typography.headlineSmall,
            placeholder = {
                Text(
                    text = stringResource(R.string.task_title_label),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.EditorTitleMinHeight)
                .focusRequester(titleFocus),
        )

        EditorPills(
            state = state,
            reminderAtLocal = reminders.reminderAtLocal,
            reminderError = reminders.error,
            actions = actions,
            reminderActions = reminderActions,
            repeatActions = repeatActions,
            permissionNotice = permissionNotice,
            focus = pillFocus,
        )

        val onPickSubtaskDue = { actions.onOpenPanel(EditorPanel.DateTime(PickerTarget.SUBTASK_DUE)) }
        if (state.taskId == null) {
            SubtaskDraftsSection(
                state = state,
                actions = subtaskDraftActions,
                onPickSubtaskDue = onPickSubtaskDue,
            )
        } else {
            SubtasksSection(
                state = subtasks,
                onNewTitleChange = subtaskActions.onNewTitleChange,
                onAdd = subtaskActions.onAdd,
                onToggle = subtaskActions.onToggle,
                onStartEdit = subtaskActions.onStartEdit,
                onEditTitleChange = subtaskActions.onEditTitleChange,
                onEditDueAtChange = subtaskActions.onEditDueAtChange,
                onPickSubtaskDue = onPickSubtaskDue,
                onEditAssigneeChange = subtaskActions.onEditAssigneeChange,
                onSaveEdit = subtaskActions.onSaveEdit,
                onCancelEdit = subtaskActions.onCancelEdit,
                onDelete = subtaskActions.onDelete,
            )
        }

        OutlinedTextField(
            value = state.description,
            onValueChange = actions.onDescriptionChange,
            label = { Text(stringResource(R.string.task_notes_label)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditorHeader(state: TaskEditorUiState, actions: TaskEditorActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ActionIcon(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.quick_add_close),
            onClick = actions.onRequestClose,
        )
        Spacer(Modifier.weight(1f))
        Box {
            ActionIcon(
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.task_editor_menu),
                onClick = { menuExpanded = true },
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (state.taskId != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.row_action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            actions.onDelete()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_editor_discard_changes)) },
                    enabled = state.isDirty,
                    onClick = {
                        menuExpanded = false
                        actions.onRequestClose()
                    },
                )
            }
        }
        Button(
            onClick = actions.onSave,
            enabled = state.title.isNotBlank() && !state.isSaving,
        ) {
            Text(stringResource(R.string.task_save))
        }
    }
}

@Composable
private fun EditorRoute(state: TaskEditorUiState, onPickList: () -> Unit, focus: PillFocus) {
    if (state.isBoardTask) {
        val board = state.boardName
        val column = state.columnName
        if (board != null && column != null) {
            val description = stringResource(R.string.task_route_board_description, board, column)
            Text(
                text = stringResource(R.string.task_route_path, board, column),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
        return
    }
    val listName = state.lists.firstOrNull { it.list.id == state.listId }?.list?.name
    val lists = stringResource(R.string.task_route_lists)
    val label = if (listName == null) lists else stringResource(R.string.task_route_path, lists, listName)
    val description = stringResource(R.string.task_route_description, listName ?: lists)
    TextButton(
        onClick = onPickList,
        modifier = Modifier
            .heightIn(min = Spacing.MinTouchTarget)
            .focusRequester(focus.list)
            .semantics { contentDescription = description },
    ) {
        Text(label)
    }
}
