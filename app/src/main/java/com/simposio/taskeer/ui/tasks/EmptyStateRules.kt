package com.simposio.taskeer.ui.tasks

import androidx.annotation.StringRes
import com.simposio.taskeer.R

enum class EmptyScreen { MY_DAY, LIST, UPCOMING, ALL_TASKS }

enum class EmptyAction { ADD_TASK, SHOW_SUGGESTIONS }

data class EmptyStateSpec(
    @param:StringRes val textRes: Int,
    @param:StringRes val actionRes: Int,
    val action: EmptyAction,
    val actionCount: Int? = null,
)

fun emptyStateFor(screen: EmptyScreen, suggestionCount: Int = 0): EmptyStateSpec =
    when (screen) {
        EmptyScreen.MY_DAY ->
            if (suggestionCount > 0) {
                EmptyStateSpec(
                    R.string.my_day_empty_with_suggestions,
                    R.string.empty_action_show_suggestions,
                    EmptyAction.SHOW_SUGGESTIONS,
                    actionCount = suggestionCount,
                )
            } else {
                EmptyStateSpec(R.string.my_day_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
            }
        EmptyScreen.LIST ->
            EmptyStateSpec(R.string.list_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
        EmptyScreen.UPCOMING ->
            EmptyStateSpec(R.string.upcoming_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
        EmptyScreen.ALL_TASKS ->
            EmptyStateSpec(R.string.all_tasks_empty, R.string.empty_action_add_task, EmptyAction.ADD_TASK)
    }
