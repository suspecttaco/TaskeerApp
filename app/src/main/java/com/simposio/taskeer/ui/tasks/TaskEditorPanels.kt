package com.simposio.taskeer.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.navigation.editorRowEnter
import com.simposio.taskeer.ui.navigation.editorRowExit
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

// El destino puede no estar compuesto todavía al volver de un panel; perder el foco no debe tumbar la hoja.
internal fun FocusRequester.requestFocusIfAttached() {
    runCatching { requestFocus() }
}

// Recuerda el último valor no nulo para que un AnimatedVisibility no vacíe su contenido mientras sale.
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

@Composable
internal fun PanelScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocusIfAttached() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.task_panel_back),
                onClick = onBack,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
            content = content,
        )
    }
}

@Composable
internal fun DateTimePanel(
    target: PickerTarget,
    pickedEpochDay: Long?,
    initialDate: LocalDate?,
    initialTime: LocalTime,
    onPickDate: (Long) -> Unit,
    onPickTime: (LocalTime) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (target) {
        PickerTarget.TASK_DUE -> R.string.task_panel_due_title
        PickerTarget.REMINDER -> R.string.task_panel_reminder_date_title
        PickerTarget.SUBTASK_DUE -> R.string.task_panel_subtask_due_title
    }
    val animated = rememberAnimationsEnabled()
    PanelScaffold(title = stringResource(title), onBack = onBack, modifier = modifier) {
        if (pickedEpochDay == null) {
            DueDatePickerContent(
                initialDate = initialDate,
                onDatePicked = { onPickDate(it.toEpochDay()) },
                compact = true,
            )
            AnimatedVisibility(
                visible = target == PickerTarget.TASK_DUE && initialDate != null,
                enter = editorRowEnter(animated),
                exit = editorRowExit(animated),
            ) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.task_due_at_remove))
                }
            }
        } else {
            DueTimePickerContent(initialTime = initialTime, onTimePicked = onPickTime)
        }
    }
}

@Composable
internal fun ListPanel(
    state: TaskEditorUiState,
    onListChosen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelScaffold(
        title = stringResource(R.string.task_panel_lists_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        DestinationOptions(
            destinations = emptyList(),
            selectedColumnId = null,
            onSelectColumn = {},
            lists = state.lists,
            selectedListId = state.listId,
            onSelectList = onListChosen,
            showBoards = false,
            error = state.error,
        )
    }
}

@Composable
internal fun DiscardPanel(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelScaffold(
        title = stringResource(R.string.task_discard_title),
        onBack = onKeepEditing,
        modifier = modifier,
    ) {
        Button(
            onClick = onDiscard,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.task_discard_confirm))
        }
        OutlinedButton(onClick = onKeepEditing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.task_discard_keep))
        }
    }
}
