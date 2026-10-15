package com.simposio.taskeer.ui.navigation

enum class AppDestination(val route: String) {
    HOME("home"),
    MY_DAY("my_day"),
    WORKSPACES("workspaces"),
    SETTINGS("settings"),
    BOARD_COLUMNS("board_columns/{boardId}"),
    ;

    companion object {
        val START: AppDestination = HOME

        val topLevel: List<AppDestination> = listOf(HOME, MY_DAY, WORKSPACES, SETTINGS)

        fun boardColumnsRoute(boardId: String): String = "board_columns/$boardId"

        fun fromRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
