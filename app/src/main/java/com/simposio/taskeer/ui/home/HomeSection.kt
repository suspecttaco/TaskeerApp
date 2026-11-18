package com.simposio.taskeer.ui.home

import com.simposio.taskeer.ui.navigation.AppDestination

enum class HomeSection(val destination: AppDestination, val isPlaceholder: Boolean = false) {
    MY_DAY(AppDestination.MY_DAY),
    UPCOMING_7_DAYS(AppDestination.UPCOMING_7_DAYS),
    ALL_TASKS(AppDestination.ALL_TASKS),
    CALENDAR(AppDestination.CALENDAR, isPlaceholder = true),
}

object HomeLayout {
    const val SEARCH_ENABLED: Boolean = false
}

fun tileLabel(name: String, activeCount: Int): String = "$name, $activeCount"
