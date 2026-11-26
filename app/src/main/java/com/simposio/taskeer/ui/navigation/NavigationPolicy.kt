package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.ui.home.HomeSection

data class NavSpec(
    val popUpToRoute: String,
    val popUpToInclusive: Boolean,
    val saveState: Boolean,
    val restoreState: Boolean,
    val launchSingleTop: Boolean,
)

fun homeNavSpec(): NavSpec = NavSpec(
    popUpToRoute = AppDestination.HOME.route,
    popUpToInclusive = false,
    saveState = false,
    restoreState = false,
    launchSingleTop = true,
)

// Cada entrada reconstruye su estado desde Room, por eso no se guarda ni se restaura.
fun sectionNavSpec(section: HomeSection): NavSpec = NavSpec(
    popUpToRoute = if (section == HomeSection.MY_DAY) AppDestination.MY_DAY.route else AppDestination.HOME.route,
    popUpToInclusive = false,
    saveState = false,
    restoreState = false,
    launchSingleTop = true,
)

sealed interface BackStep {
    data object None : BackStep

    data object Pop : BackStep

    data class Navigate(val route: String) : BackStep
}

fun backStep(destination: AppDestination?, boardId: String?, hasPrevious: Boolean): BackStep {
    val fallback = when (destination) {
        null, AppDestination.MY_DAY -> return BackStep.None
        AppDestination.HOME, AppDestination.NOTIFICATIONS, AppDestination.SETTINGS -> AppDestination.MY_DAY.route
        AppDestination.UPCOMING_7_DAYS,
        AppDestination.ALL_TASKS,
        AppDestination.CALENDAR,
        AppDestination.LIST,
        AppDestination.BOARD,
        AppDestination.SEARCH,
        -> AppDestination.HOME.route
        AppDestination.LOGIN, AppDestination.VERIFY_EMAIL -> AppDestination.SETTINGS.route
        AppDestination.REGISTER, AppDestination.RESET_PASSWORD -> AppDestination.LOGIN.route
        AppDestination.CHANGE_PASSWORD, AppDestination.REGENERATE_CODES -> AppDestination.SETTINGS.route
        AppDestination.BOARD_COLUMNS -> boardId?.let(AppDestination::boardRoute) ?: return BackStep.None
    }
    return if (hasPrevious) BackStep.Pop else BackStep.Navigate(fallback)
}

// Un Intent reentregado (recientes, proceso restaurado) no debe volver a abrir el visor.
fun shouldOpenViewer(
    hasExtra: Boolean,
    isRestored: Boolean,
    launchedFromHistory: Boolean,
): Boolean = hasExtra && !isRestored && !launchedFromHistory
