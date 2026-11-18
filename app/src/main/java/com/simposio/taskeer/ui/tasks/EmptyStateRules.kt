package com.simposio.taskeer.ui.tasks

import androidx.annotation.StringRes
import com.simposio.taskeer.R

enum class EmptyScreen { MY_DAY, INBOX, LIST, PLANNED }

enum class EmptyAction { ADD_TASK, SHOW_ALL, GO_TO_INBOX }

data class EmptyStateSpec(
    @param:StringRes val textRes: Int,
    @param:StringRes val actionRes: Int,
    val action: EmptyAction,
)

fun emptyStateFor(screen: EmptyScreen, hasTasks: Boolean, filterActive: Boolean): EmptyStateSpec =
    when (screen) {
        EmptyScreen.MY_DAY ->
            if (hasTasks && filterActive) {
                EmptyStateSpec(R.string.my_day_filter_empty, R.string.empty_action_show_all, EmptyAction.SHOW_ALL)
            } else {
                EmptyStateSpec(R.string.my_day_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
            }
        EmptyScreen.INBOX ->
            EmptyStateSpec(R.string.inbox_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
        EmptyScreen.LIST ->
            EmptyStateSpec(R.string.list_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
        EmptyScreen.PLANNED ->
            EmptyStateSpec(R.string.planned_empty, R.string.empty_action_go_inbox, EmptyAction.GO_TO_INBOX)
    }
