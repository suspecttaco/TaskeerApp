package com.simposio.taskeer.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.simposio.taskeer.R

internal fun RowAction.icon(): ImageVector = when (this) {
    RowAction.COMPLETE -> Icons.Filled.Check
    RowAction.REOPEN -> Icons.Filled.Replay
    RowAction.ADD_TO_MY_DAY -> Icons.Filled.Star
    RowAction.REMOVE_FROM_MY_DAY -> Icons.Filled.StarBorder
    RowAction.KEEP_TODAY -> Icons.Filled.WbSunny
    RowAction.DISMISS -> Icons.Filled.Close
    RowAction.RESCHEDULE_TOMORROW -> Icons.Filled.Today
    RowAction.RESCHEDULE_NEXT_WEEK -> Icons.Filled.DateRange
    RowAction.RESCHEDULE_SOMEDAY -> Icons.Filled.EventBusy
    RowAction.PICK_DATE -> Icons.Filled.CalendarMonth
    RowAction.ASSIGN_TO_BOARD -> Icons.AutoMirrored.Filled.DriveFileMove
    RowAction.MOVE_TO_LIST -> Icons.AutoMirrored.Filled.PlaylistAdd
    RowAction.DELETE -> Icons.Filled.Delete
}

@StringRes
internal fun RowAction.labelRes(): Int = when (this) {
    RowAction.COMPLETE -> R.string.row_action_complete
    RowAction.REOPEN -> R.string.row_action_reopen
    RowAction.ADD_TO_MY_DAY -> R.string.row_action_add_my_day
    RowAction.REMOVE_FROM_MY_DAY -> R.string.row_action_remove_my_day
    RowAction.KEEP_TODAY -> R.string.row_action_keep_today
    RowAction.DISMISS -> R.string.row_action_dismiss
    RowAction.RESCHEDULE_TOMORROW -> R.string.row_action_tomorrow
    RowAction.RESCHEDULE_NEXT_WEEK -> R.string.row_action_next_week
    RowAction.RESCHEDULE_SOMEDAY -> R.string.row_action_someday
    RowAction.PICK_DATE -> R.string.row_action_pick_date
    RowAction.ASSIGN_TO_BOARD -> R.string.row_action_assign
    RowAction.MOVE_TO_LIST -> R.string.row_action_move_to_list
    RowAction.DELETE -> R.string.row_action_delete
}

@StringRes
internal fun RowAction.swipeLabelRes(): Int = when (this) {
    RowAction.COMPLETE -> R.string.swipe_complete
    RowAction.REMOVE_FROM_MY_DAY -> R.string.swipe_remove_today
    RowAction.RESCHEDULE_TOMORROW -> R.string.swipe_tomorrow
    RowAction.DELETE -> R.string.swipe_delete
    else -> labelRes()
}

@Composable
internal fun RowAction.swipeContainerColor(): Color = when (this) {
    RowAction.DELETE -> MaterialTheme.colorScheme.errorContainer
    RowAction.COMPLETE, RowAction.REOPEN, RowAction.KEEP_TODAY -> MaterialTheme.colorScheme.primaryContainer
    RowAction.RESCHEDULE_TOMORROW,
    RowAction.RESCHEDULE_NEXT_WEEK,
    RowAction.RESCHEDULE_SOMEDAY,
    RowAction.PICK_DATE,
    -> MaterialTheme.colorScheme.tertiaryContainer
    RowAction.ADD_TO_MY_DAY,
    RowAction.REMOVE_FROM_MY_DAY,
    RowAction.DISMISS,
    RowAction.ASSIGN_TO_BOARD,
    RowAction.MOVE_TO_LIST,
    -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
internal fun RowAction.swipeContentColor(): Color = when (this) {
    RowAction.DELETE -> MaterialTheme.colorScheme.onErrorContainer
    RowAction.COMPLETE, RowAction.REOPEN, RowAction.KEEP_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer
    RowAction.RESCHEDULE_TOMORROW,
    RowAction.RESCHEDULE_NEXT_WEEK,
    RowAction.RESCHEDULE_SOMEDAY,
    RowAction.PICK_DATE,
    -> MaterialTheme.colorScheme.onTertiaryContainer
    RowAction.ADD_TO_MY_DAY,
    RowAction.REMOVE_FROM_MY_DAY,
    RowAction.DISMISS,
    RowAction.ASSIGN_TO_BOARD,
    RowAction.MOVE_TO_LIST,
    -> MaterialTheme.colorScheme.onSecondaryContainer
}
