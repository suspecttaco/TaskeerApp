package com.simposio.taskeer.ui.navigation

import androidx.navigation.NavController

fun NavController.navigateTopLevel(destination: AppDestination) {
    val spec = topLevelNavSpec()
    navigate(destination.route) {
        popUpTo(spec.popUpToRoute) { saveState = spec.saveState }
        restoreState = spec.restoreState
        launchSingleTop = spec.launchSingleTop
    }
}
