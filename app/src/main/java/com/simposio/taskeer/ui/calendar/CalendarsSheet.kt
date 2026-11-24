package com.simposio.taskeer.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.shell.TrackOpenSheet
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme
import kotlinx.coroutines.launch

private sealed interface CalendarsRow {
    val key: String

    data class Account(val name: String) : CalendarsRow {
        override val key: String get() = "a:$name"
    }

    data class Entry(val option: CalendarOption) : CalendarsRow {
        override val key: String get() = "c:${option.calendar.id}"
    }
}

private fun rowsOf(calendars: List<CalendarOption>): List<CalendarsRow> =
    calendars.groupBy { it.calendar.accountName }.flatMap { (account, options) ->
        listOf<CalendarsRow>(CalendarsRow.Account(account)) + options.map { CalendarsRow.Entry(it) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(
    calendars: List<CalendarOption>,
    onToggle: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.menu_my_calendars)
    val dragDescription = stringResource(R.string.quick_add_drag_handle)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.semantics { contentDescription = dragDescription },
            )
        },
    ) {
        TrackOpenSheet()
        Column(modifier = Modifier.fillMaxWidth().semantics { paneTitle = title }) {
            Row(
                modifier = Modifier.padding(start = Spacing.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                ActionIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quick_add_close),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } },
                )
            }
            if (calendars.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_no_calendars),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.ScreenPadding * 2),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false).navigationBarsPadding()) {
                    items(rowsOf(calendars), key = { it.key }) { row ->
                        when (row) {
                            is CalendarsRow.Account -> Text(
                                text = row.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
                                    .semantics { heading() },
                            )
                            is CalendarsRow.Entry -> CalendarCheckRow(row.option, onToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCheckRow(option: CalendarOption, onToggle: (Long, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .toggleable(
                value = option.visible,
                role = Role.Checkbox,
                onValueChange = { onToggle(option.calendar.id, it) },
            )
            .padding(horizontal = Spacing.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.InlineGap),
    ) {
        Checkbox(checked = option.visible, onCheckedChange = null)
        Box(
            Modifier
                .size(Spacing.ActionIconSize / 2)
                .clip(CircleShape)
                .background(Color(option.calendar.colorArgb)),
        )
        Text(text = option.calendar.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun CalendarsSheetPreview() {
    TaskeerTheme {
        CalendarsSheet(
            calendars = listOf(
                CalendarOption(DeviceCalendar(1, "Personal", "yo@example.com", 0xFF3F51B5.toInt(), true), true),
                CalendarOption(DeviceCalendar(2, "Festivos", "yo@example.com", 0xFFE91E63.toInt(), false), false),
            ),
            onToggle = { _, _ -> },
            onClose = {},
        )
    }
}
