package com.simposio.taskeer.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.simposio.taskeer.ui.home.HomeSection

fun NavController.navigateHome() {
    val spec = homeNavSpec()
    navigate(AppDestination.HOME.route) { applySpec(spec) }
}

fun NavController.navigateSection(section: HomeSection) {
    val spec = sectionNavSpec(section)
    navigate(section.destination.route) { applySpec(spec) }
}

private fun NavOptionsBuilder.applySpec(spec: NavSpec) {
    popUpTo(spec.popUpToRoute) {
        inclusive = spec.popUpToInclusive
        saveState = spec.saveState
    }
    restoreState = spec.restoreState
    launchSingleTop = spec.launchSingleTop
}
