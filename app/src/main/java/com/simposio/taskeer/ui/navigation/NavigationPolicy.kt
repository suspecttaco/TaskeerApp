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

sealed interface BackStep {
    data object None : BackStep

    data object Pop : BackStep

    data class Navigate(val route: String) : BackStep
}

fun backStep(destination: AppDestination?, boardId: String?, hasPrevious: Boolean): BackStep {
    val fallback = when {
        destination == AppDestination.NOTIFICATIONS -> AppDestination.MY_DAY.route
        boardId != null -> destination?.upRoute(boardId)
        else -> null
    } ?: return BackStep.None
    return if (hasPrevious) BackStep.Pop else BackStep.Navigate(fallback)
}

// Un Intent reentregado (recientes, proceso restaurado) no debe volver a abrir el visor.
fun shouldOpenViewer(
    hasExtra: Boolean,
    isRestored: Boolean,
    launchedFromHistory: Boolean,
): Boolean = hasExtra && !isRestored && !launchedFromHistory
