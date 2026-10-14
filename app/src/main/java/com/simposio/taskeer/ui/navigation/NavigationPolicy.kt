package com.simposio.taskeer.ui.navigation

data class TopLevelNavSpec(
    val popUpToRoute: String,
    val saveState: Boolean,
    val restoreState: Boolean,
    val launchSingleTop: Boolean,
)

fun topLevelNavSpec(): TopLevelNavSpec = TopLevelNavSpec(
    popUpToRoute = AppDestination.START.route,
    saveState = true,
    restoreState = true,
    launchSingleTop = true,
)
