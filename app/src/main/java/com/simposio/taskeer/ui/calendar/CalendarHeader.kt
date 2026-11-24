package com.simposio.taskeer.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.calendar.CalendarMode
import com.simposio.taskeer.domain.calendar.periodFor
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.theme.Spacing

class CalendarHeaderActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToday: () -> Unit,
    val onToggleMonth: () -> Unit,
    val onOpenCalendars: () -> Unit,
    val onSelectMode: (CalendarMode) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHeader(
    state: CalendarUiState,
    actions: CalendarHeaderActions,
    modifier: Modifier = Modifier,
) {
    val title = periodTitle(state.mode, periodFor(state.mode, state.anchor, state.firstDay), state.anchor)
    val gridState = stringResource(
        if (state.monthExpanded) R.string.calendar_grid_expanded else R.string.calendar_grid_collapsed,
    )
    val toggleDescription = stringResource(R.string.calendar_toggle_grid, title)
    val calendarsEnabled = state.access.read
    val calendarsDescription = stringResource(
        if (calendarsEnabled) R.string.menu_my_calendars else R.string.calendar_my_calendars_unavailable,
    )
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(state.mode.previousRes()),
                onClick = actions.onPrevious,
            )
            ActionIcon(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(state.mode.nextRes()),
                onClick = actions.onNext,
            )
            TextButton(
                onClick = actions.onToggleMonth,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Spacing.MinTouchTarget)
                    .semantics {
                        role = Role.Button
                        contentDescription = toggleDescription
                        stateDescription = gridState
                    },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                DecorativeIcon(
                    icon = if (state.monthExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    modifier = Modifier.padding(start = Spacing.ItemGap),
                )
            }
            TextButton(onClick = actions.onToday, modifier = Modifier.heightIn(min = Spacing.MinTouchTarget)) {
                Text(stringResource(R.string.calendar_today))
            }
            ActionIcon(
                icon = Icons.Filled.CalendarMonth,
                contentDescription = calendarsDescription,
                onClick = actions.onOpenCalendars,
                enabled = calendarsEnabled,
            )
        }
        val modesDescription = stringResource(R.string.calendar_modes_description)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = modesDescription },
        ) {
            val modes = CalendarMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { actions.onSelectMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = { Text(text = stringResource(mode.labelRes()), maxLines = 1) },
                )
            }
        }
    }
}

@StringRes
private fun CalendarMode.labelRes(): Int = when (this) {
    CalendarMode.AGENDA -> R.string.calendar_mode_agenda
    CalendarMode.DAY -> R.string.calendar_mode_day
    CalendarMode.THREE_DAYS -> R.string.calendar_mode_three_days
    CalendarMode.WEEK -> R.string.calendar_mode_week
}

@StringRes
private fun CalendarMode.previousRes(): Int = when (this) {
    CalendarMode.AGENDA -> R.string.calendar_prev_agenda
    CalendarMode.DAY -> R.string.calendar_prev_day
    CalendarMode.THREE_DAYS -> R.string.calendar_prev_three_days
    CalendarMode.WEEK -> R.string.calendar_prev_week
}

@StringRes
private fun CalendarMode.nextRes(): Int = when (this) {
    CalendarMode.AGENDA -> R.string.calendar_next_agenda
    CalendarMode.DAY -> R.string.calendar_next_day
    CalendarMode.THREE_DAYS -> R.string.calendar_next_three_days
    CalendarMode.WEEK -> R.string.calendar_next_week
}
