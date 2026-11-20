package com.simposio.taskeer.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.LocalUser
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.RepeatPreset
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorRowEnter
import com.simposio.taskeer.ui.navigation.editorRowExit
import com.simposio.taskeer.ui.navigation.editorSizeSpec
import com.simposio.taskeer.ui.navigation.editorValueTransition
import com.simposio.taskeer.ui.navigation.panelEnter
import com.simposio.taskeer.ui.navigation.panelExit
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalDateTime

internal class PillFocus {
    val due = FocusRequester()
    val reminder = FocusRequester()
    val list = FocusRequester()

    fun forPanel(panel: EditorPanel): FocusRequester? = when (panel) {
        is EditorPanel.DateTime -> when (panel.target) {
            PickerTarget.TASK_DUE -> due
            PickerTarget.REMINDER -> reminder
            PickerTarget.SUBTASK_DUE -> null
        }
        EditorPanel.ListPicker -> list
        EditorPanel.DiscardConfirm, EditorPanel.None -> null
    }
}

@Composable
internal fun EditorPills(
    state: TaskEditorUiState,
    reminderAtLocal: LocalDateTime?,
    reminderError: String?,
    actions: TaskEditorActions,
    reminderActions: ReminderActions,
    repeatActions: RepeatActions,
    permissionNotice: @Composable () -> Unit,
    focus: PillFocus,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
        val completeLabel = stringResource(
            if (state.completed) R.string.task_pill_completed else R.string.task_pill_complete,
        )
        FilterChip(
            selected = state.completed,
            onClick = actions.onToggleCompleted,
            enabled = state.taskId != null && !state.isSaving,
            label = { AnimatedPillText(completeLabel) },
            leadingIcon = { DecorativeIcon(Icons.Filled.CheckCircle) },
            modifier = Modifier.pill(),
        )

        val myDayLabel = stringResource(
            if (state.inMyDay) R.string.task_pill_in_my_day else R.string.task_pill_my_day,
        )
        FilterChip(
            selected = state.inMyDay,
            onClick = actions.onToggleMyDay,
            enabled = !state.isSaving,
            label = { AnimatedPillText(myDayLabel) },
            leadingIcon = { DecorativeIcon(Icons.Filled.WbSunny) },
            modifier = Modifier.pill(),
        )

        ValuePill(
            name = stringResource(R.string.task_pill_due),
            value = state.dueAtLocal?.format(DUE_DATE_TIME_FORMATTER) ?: stringResource(R.string.task_due_at_none),
            icon = Icons.Filled.Event,
            onClick = { actions.onOpenPanel(EditorPanel.DateTime(PickerTarget.TASK_DUE)) },
            modifier = Modifier.focusRequester(focus.due),
        )

        ReminderPill(reminderAtLocal, actions, reminderActions, focus)
        val shownReminderError = rememberLastNonNull(reminderError)
        AnimatedVisibility(
            visible = reminderError != null,
            enter = panelEnter(animated),
            exit = panelExit(animated),
        ) {
            Text(
                text = shownReminderError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(Modifier.animateContentSize(editorSizeSpec(animated))) { permissionNotice() }

        RepeatPill(state, repeatActions)
        val customRepeat = rememberLastNonNull(
            state.takeIf { it.repeatPreset == RepeatPreset.CUSTOM && it.repeat != null },
        )
        AnimatedVisibility(
            visible = state.repeatPreset == RepeatPreset.CUSTOM && state.repeat != null,
            enter = editorRowEnter(animated),
            exit = editorRowExit(animated),
        ) {
            customRepeat?.let { RepeatCustomFields(state = it, actions = repeatActions) }
        }

        PriorityPill(state.priority, actions.onPriorityChange)
        AssigneePill(state.assigneeId, actions.onAssigneeChange)
    }
}

@Composable
private fun PillMenu(
    pill: @Composable (onClick: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    returnFocusTo: FocusRequester? = null,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            wasExpanded = true
        } else if (wasExpanded) {
            wasExpanded = false
            returnFocusTo?.requestFocusIfAttached()
        }
    }
    Box(modifier) {
        pill { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

@Composable
private fun ReminderPill(
    reminderAtLocal: LocalDateTime?,
    actions: TaskEditorActions,
    reminderActions: ReminderActions,
    focus: PillFocus,
) {
    PillMenu(
        returnFocusTo = focus.reminder,
        pill = { onClick ->
            ValuePill(
                name = stringResource(R.string.reminder_title),
                value = reminderAtLocal?.format(DUE_DATE_TIME_FORMATTER)
                    ?: stringResource(R.string.task_pill_no_reminder),
                icon = Icons.Filled.Alarm,
                onClick = onClick,
                modifier = Modifier.focusRequester(focus.reminder),
            )
        },
    ) { dismiss ->
        if (reminderAtLocal != null) {
            DropdownMenuItem(
                text = { Text(reminderAtLocal.format(DUE_DATE_TIME_FORMATTER)) },
                onClick = {},
                enabled = false,
            )
            MenuItem(R.string.reminder_remove) {
                dismiss()
                reminderActions.onCancel()
            }
        } else {
            MenuItem(R.string.reminder_later_today) {
                dismiss()
                reminderActions.onLaterToday()
            }
            MenuItem(R.string.reminder_tomorrow) {
                dismiss()
                reminderActions.onTomorrow()
            }
            MenuItem(R.string.reminder_next_week) {
                dismiss()
                reminderActions.onNextWeek()
            }
            MenuItem(R.string.reminder_custom) {
                dismiss()
                actions.onOpenPanel(EditorPanel.DateTime(PickerTarget.REMINDER))
            }
        }
    }
}

@Composable
private fun RepeatPill(state: TaskEditorUiState, actions: RepeatActions) {
    PillMenu(
        pill = { onClick ->
            ValuePill(
                name = stringResource(R.string.task_repeat_label),
                value = stringResource(state.repeatPreset.labelRes()),
                icon = Icons.Filled.Repeat,
                onClick = onClick,
            )
        },
    ) { dismiss ->
        if (!state.repeatEnabled) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_repeat_hint_needs_due)) },
                onClick = {},
                enabled = false,
            )
        }
        RepeatPreset.entries.forEach { preset ->
            val current = preset == state.repeatPreset
            DropdownMenuItem(
                text = { Text(stringResource(preset.labelRes())) },
                leadingIcon = if (current) {
                    { DecorativeIcon(Icons.Filled.Check) }
                } else {
                    null
                },
                enabled = state.repeatEnabled,
                onClick = {
                    dismiss()
                    actions.onPresetChange(preset)
                },
                modifier = Modifier.semantics { selected = current },
            )
        }
    }
}

@Composable
private fun PriorityPill(priority: TaskPriority, onChange: (TaskPriority) -> Unit) {
    PillMenu(
        pill = { onClick ->
            ValuePill(
                name = stringResource(R.string.task_priority_label),
                value = stringResource(priority.labelRes()),
                icon = Icons.Filled.Flag,
                onClick = onClick,
            )
        },
    ) { dismiss ->
        TaskPriority.entries.forEach { option ->
            val current = option == priority
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = if (current) {
                    { DecorativeIcon(Icons.Filled.Check) }
                } else {
                    null
                },
                onClick = {
                    dismiss()
                    onChange(option)
                },
                modifier = Modifier.semantics { selected = current },
            )
        }
    }
}

@Composable
private fun AssigneePill(assigneeId: String?, onChange: (String?) -> Unit) {
    PillMenu(
        pill = { onClick ->
            ValuePill(
                name = stringResource(R.string.task_assignee_label),
                value = stringResource(R.string.task_assignee_me),
                icon = Icons.Filled.Person,
                onClick = onClick,
            )
        },
    ) { dismiss ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.task_assignee_me)) },
            leadingIcon = if (assigneeId == LocalUser.ID) {
                { DecorativeIcon(Icons.Filled.Check) }
            } else {
                null
            },
            onClick = {
                dismiss()
                onChange(LocalUser.ID)
            },
        )
    }
}

@Composable
private fun MenuItem(@StringRes label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun ValuePill(
    name: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.task_pill_description, name, value)
    AssistChip(
        onClick = onClick,
        label = { AnimatedPillText(description) },
        leadingIcon = { DecorativeIcon(icon) },
        modifier = modifier
            .pill()
            .semantics { contentDescription = description },
    )
}

@Composable
private fun AnimatedPillText(text: String) {
    val animated = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = text,
        transitionSpec = { editorValueTransition(animated) },
        label = "pillText",
    ) { Text(it) }
}

private fun Modifier.pill(): Modifier = fillMaxWidth().heightIn(min = Spacing.EditorPillMinHeight)

@StringRes
internal fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.task_priority_low
    TaskPriority.MEDIUM -> R.string.task_priority_medium
    TaskPriority.HIGH -> R.string.task_priority_high
    TaskPriority.URGENT -> R.string.task_priority_urgent
}
