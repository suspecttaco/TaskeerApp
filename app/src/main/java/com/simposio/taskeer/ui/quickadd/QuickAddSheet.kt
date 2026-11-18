package com.simposio.taskeer.ui.quickadd

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.TaskPriority
import com.simposio.taskeer.domain.task.DEFAULT_DUE_TIME
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.PriorityDot
import com.simposio.taskeer.ui.components.labelRes
import com.simposio.taskeer.ui.tasks.DestinationOptions
import com.simposio.taskeer.ui.tasks.DueDatePickerContent
import com.simposio.taskeer.ui.tasks.DueTimePickerContent
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class QuickAddActions(
    val onDismiss: () -> Unit,
    val onBack: () -> BackResult,
    val onTitleChange: (String) -> Unit,
    val onTogglePanel: (QuickAddPanel) -> Unit,
    val onSelectList: (String) -> Unit,
    val onSelectColumn: (String) -> Unit,
    val onSelectPriority: (TaskPriority) -> Unit,
    val onSelectPreset: (QuickAddDatePreset) -> Unit,
    val onSelectDate: (LocalDate?) -> Unit,
    val onSelectTime: (LocalTime?) -> Unit,
    val onAdd: () -> Unit,
)

@Composable
fun rememberQuickAddHost(viewModelFactory: ViewModelProvider.Factory, context: QuickAddContext): () -> Unit {
    val viewModel: QuickAddViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isOpen) {
        QuickAddSheet(
            state = state,
            actions = QuickAddActions(
                onDismiss = viewModel::close,
                onBack = viewModel::onBack,
                onTitleChange = viewModel::onTitleChange,
                onTogglePanel = viewModel::togglePanel,
                onSelectList = viewModel::selectList,
                onSelectColumn = viewModel::selectColumn,
                onSelectPriority = viewModel::selectPriority,
                onSelectPreset = viewModel::selectPreset,
                onSelectDate = viewModel::selectDate,
                onSelectTime = viewModel::selectTime,
                onAdd = viewModel::add,
            ),
        )
    }
    return remember(viewModel, context) { { viewModel.open(context) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(state: QuickAddUiState, actions: QuickAddActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val title = stringResource(R.string.quick_add_title)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    val titleFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var panelWasOpen by remember { mutableStateOf(false) }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) sheetState.expand()
    }
    LaunchedEffect(Unit) {
        titleFocus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(state.panel) {
        when (state.panel) {
            null -> if (panelWasOpen) titleFocus.requestFocus()
            QuickAddPanel.DATE, QuickAddPanel.TIME -> {
                keyboard?.hide()
                focusManager.clearFocus()
                sheetState.expand()
            }
            QuickAddPanel.DESTINATION, QuickAddPanel.PRIORITY -> {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        }
        panelWasOpen = state.panel != null
    }
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
    ) {
        // Dentro del contenido: el sheet es un diálogo con su propio despachador de Atrás y el de la actividad no le llega.
        BackHandler(enabled = state.panel != null) { actions.onBack() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenPadding)
                .padding(bottom = Spacing.ScreenPadding)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(Spacing.SheetContentGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quick_add_close),
                    onClick = actions.onDismiss,
                )
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitleChange,
                label = { Text(stringResource(R.string.task_title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onAdd() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
            )
            state.error?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            QuickAddRows(state, actions)
            Button(
                onClick = actions.onAdd,
                enabled = state.title.isNotBlank() && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.quick_add_add))
            }
        }
    }
}

@Composable
private fun QuickAddRows(state: QuickAddUiState, actions: QuickAddActions) {
    ExpandableRow(
        label = stringResource(R.string.quick_add_field_destination),
        value = state.destinationLabel ?: stringResource(R.string.destination_inbox),
        expanded = state.panel == QuickAddPanel.DESTINATION,
        onClick = { actions.onTogglePanel(QuickAddPanel.DESTINATION) },
    ) {
        DestinationOptions(
            destinations = state.destinations,
            lists = state.lists,
            selectedListId = (state.destination as? QuickAddDestination.InList)?.listId,
            selectedColumnId = (state.destination as? QuickAddDestination.InColumn)?.columnId,
            onSelectList = actions.onSelectList,
            onSelectColumn = actions.onSelectColumn,
        )
    }
    ExpandableRow(
        label = stringResource(R.string.quick_add_field_date),
        value = state.dueDate?.format(DATE_FORMATTER) ?: stringResource(R.string.quick_add_date_none),
        expanded = state.panel == QuickAddPanel.DATE,
        onClick = { actions.onTogglePanel(QuickAddPanel.DATE) },
    ) {
        DateOptions(state.dueDate, actions)
    }
    if (state.dueDate != null) {
        ExpandableRow(
            label = stringResource(R.string.quick_add_field_time),
            value = (state.dueTime ?: DEFAULT_DUE_TIME).format(TIME_FORMATTER),
            expanded = state.panel == QuickAddPanel.TIME,
            onClick = { actions.onTogglePanel(QuickAddPanel.TIME) },
        ) {
            DueTimePickerContent(
                initialTime = state.dueTime ?: DEFAULT_DUE_TIME,
                onTimePicked = actions.onSelectTime,
            )
        }
    }
    ExpandableRow(
        label = stringResource(R.string.quick_add_field_priority),
        value = stringResource(state.priority.labelRes()),
        expanded = state.panel == QuickAddPanel.PRIORITY,
        onClick = { actions.onTogglePanel(QuickAddPanel.PRIORITY) },
    ) {
        PriorityOptions(state.priority, actions.onSelectPriority)
    }
}

@Composable
private fun ExpandableRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val stateText = stringResource(if (expanded) R.string.quick_add_expanded else R.string.quick_add_collapsed)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .minimumInteractiveComponentSize()
                .semantics {
                    role = Role.Button
                    stateDescription = stateText
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DecorativeIcon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore)
        }
        if (expanded) content()
    }
}

@Composable
private fun DateOptions(selected: LocalDate?, actions: QuickAddActions) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
        ) {
            PresetChip(R.string.quick_add_date_today) { actions.onSelectPreset(QuickAddDatePreset.TODAY) }
            PresetChip(R.string.quick_add_date_tomorrow) { actions.onSelectPreset(QuickAddDatePreset.TOMORROW) }
            PresetChip(R.string.quick_add_date_next_week) { actions.onSelectPreset(QuickAddDatePreset.NEXT_WEEK) }
            PresetChip(R.string.quick_add_date_none) { actions.onSelectPreset(QuickAddDatePreset.NONE) }
            PresetChip(R.string.quick_add_date_pick) { picking = !picking }
        }
        if (picking) {
            DueDatePickerContent(initialDate = selected, onDatePicked = actions.onSelectDate)
        }
    }
}

@Composable
private fun PresetChip(@StringRes labelRes: Int, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(stringResource(labelRes)) })
}

@Composable
private fun PriorityOptions(selected: TaskPriority, onSelect: (TaskPriority) -> Unit) {
    Column {
        TaskPriority.entries.forEach { priority ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = priority == selected, onClick = { onSelect(priority) }, role = Role.RadioButton)
                    .minimumInteractiveComponentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
            ) {
                RadioButton(selected = priority == selected, onClick = null)
                PriorityDot(priority)
                Text(text = stringResource(priority.labelRes()))
            }
        }
    }
}

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Preview(showBackground = true)
@Composable
private fun QuickAddSheetPreview() {
    TaskeerTheme {
        QuickAddSheet(
            state = QuickAddUiState(isOpen = true, title = "Comprar pan", dueDate = LocalDate.of(2026, 11, 20)),
            actions = QuickAddActions({}, { BackResult.CLOSE }, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
