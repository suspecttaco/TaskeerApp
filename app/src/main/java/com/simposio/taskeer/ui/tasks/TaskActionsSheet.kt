package com.simposio.taskeer.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.RowAction
import com.simposio.taskeer.ui.components.RowActions
import com.simposio.taskeer.ui.components.RowContext
import com.simposio.taskeer.ui.components.TaskRowUi
import com.simposio.taskeer.ui.components.actionsIn
import com.simposio.taskeer.ui.components.icon
import com.simposio.taskeer.ui.components.labelRes
import com.simposio.taskeer.ui.shell.TrackOpenSheet
import com.simposio.taskeer.ui.theme.Spacing
import java.time.LocalDate

private data class SheetTarget(val row: TaskRowUi, val context: RowContext, val pickDate: Boolean)

// Devuelve la función que abre la hoja de una fila; `true` la abre ya en el selector de fecha.
@Composable
fun rememberTaskActionsSheet(
    onAction: (RowContext, String, RowAction) -> Unit,
    onPickDate: (RowContext, String, LocalDate) -> Unit,
): (TaskRowUi, RowContext, Boolean) -> Unit {
    var target by remember { mutableStateOf<SheetTarget?>(null) }
    target?.let { current ->
        TaskActionsSheet(
            actions = current.row.actionsIn(current.context),
            startAtDatePicker = current.pickDate,
            onAction = { action ->
                target = null
                onAction(current.context, current.row.id, action)
            },
            onPickDate = { date ->
                target = null
                onPickDate(current.context, current.row.id, date)
            },
            onDismiss = { target = null },
        )
    }
    return remember { { row, context, pickDate -> target = SheetTarget(row, context, pickDate) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskActionsSheet(
    actions: RowActions,
    startAtDatePicker: Boolean,
    onAction: (RowAction) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(startAtDatePicker) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val title = stringResource(R.string.actions_sheet_title)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(pickingDate) {
        if (pickingDate) sheetState.expand() else firstItem.requestFocus()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
    ) {
        TrackOpenSheet()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenPadding)
                .semantics { paneTitle = title },
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
                    onClick = onDismiss,
                )
            }
            if (pickingDate) {
                DueDatePickerContent(initialDate = null, onDatePicked = onPickDate)
            } else {
                actions.sheet.forEachIndexed { index, action ->
                    val destructive = action == RowAction.DELETE
                    val error = MaterialTheme.colorScheme.error
                    ListItem(
                        headlineContent = { Text(stringResource(action.labelRes())) },
                        leadingContent = { DecorativeIcon(action.icon()) },
                        colors = if (destructive) {
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = error,
                                leadingIconColor = error,
                            )
                        } else {
                            ListItemDefaults.colors(containerColor = Color.Transparent)
                        },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstItem) else Modifier)
                            .clickable {
                                if (action == RowAction.PICK_DATE) pickingDate = true else onAction(action)
                            },
                    )
                }
            }
        }
    }
}
