package com.simposio.taskeer.ui.navigation

enum class AppDestination(val route: String) {
    HOME("home"),
    MY_DAY("my_day"),
    WORKSPACES("workspaces"),
    SETTINGS("settings"),
    ;

    companion object {
        val START: AppDestination = HOME

        val topLevel: List<AppDestination> = listOf(HOME, MY_DAY, WORKSPACES, SETTINGS)

        fun fromRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
