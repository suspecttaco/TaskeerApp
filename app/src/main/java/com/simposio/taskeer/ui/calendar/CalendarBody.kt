package com.simposio.taskeer.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.DayItem
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.components.EmptyState
import com.simposio.taskeer.ui.components.rowItemModifier
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.emphasized
import com.simposio.taskeer.ui.upcoming.weekdayLabel
import java.time.ZoneId

@Composable
fun CalendarBody(
    rows: List<BodyRow>,
    zone: ZoneId,
    listState: LazyListState,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Spacing.ChromeClearance),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is BodyRow.DayHeader -> DayHeaderRow(row, modifier = rowItemModifier())
                is BodyRow.EventEntry -> EventRow(row.item, zone, modifier = rowItemModifier())
                is BodyRow.TaskEntry -> TaskDueRow(
                    item = row.item,
                    onOpen = { onOpenTask(row.item.task.taskId) },
                    modifier = rowItemModifier(),
                )
                is BodyRow.EmptyDay -> EmptyState(
                    icon = Icons.Filled.EventBusy,
                    text = stringResource(R.string.calendar_empty_day),
                    modifier = rowItemModifier().fillMaxWidth(),
                )
                BodyRow.EmptyMonth -> EmptyState(
                    icon = Icons.Filled.EventBusy,
                    text = stringResource(R.string.calendar_empty_month),
                    modifier = rowItemModifier().fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DayHeaderRow(row: BodyRow.DayHeader, modifier: Modifier = Modifier) {
    val label = weekdayLabel(row.date, currentLocale())
    val text = if (row.isToday) stringResource(R.string.calendar_day_today, label) else label
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.emphasized(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenPadding, vertical = Spacing.InlineGap)
            .semantics { heading() },
    )
}

@Composable
private fun EventRow(item: DayItem.Event, zone: ZoneId, modifier: Modifier = Modifier) {
    val title = item.event.title.ifBlank { stringResource(R.string.calendar_untitled) }
    val time = eventTimeText(item, zone)
    val calendar = item.calendar
    val description = if (calendar != null) {
        stringResource(R.string.event_row_description, title, time, calendar.name)
    } else {
        stringResource(R.string.event_row_description_no_calendar, title, time)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(listOfNotNull(time, calendar?.name).joinToString(" · ")) },
        leadingContent = {
            Box(
                Modifier
                    .size(Spacing.ActionIconSize / 2)
                    .clip(CircleShape)
                    .background(calendar?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

@Composable
private fun TaskDueRow(item: DayItem.Task, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val time = formatTime(item.time, currentLocale())
    val place = item.task.place
    val description = if (place != null) {
        stringResource(R.string.event_task_row_description_place, item.task.title, time, place)
    } else {
        stringResource(R.string.event_task_row_description, item.task.title, time)
    }
    ListItem(
        headlineContent = { Text(item.task.title) },
        supportingContent = { Text(listOfNotNull(time, place).joinToString(" · ")) },
        leadingContent = { DecorativeIcon(Icons.Filled.TaskAlt, tint = MaterialTheme.colorScheme.tertiary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics { contentDescription = description },
    )
}

