package com.simposio.taskeer.ui.navigation

enum class AppDestination(val route: String) {
    MY_DAY("my_day"),
    INBOX("inbox"),
    WORKSPACES("workspaces"),
    SETTINGS("settings"),
    BOARD("board/{boardId}"),
    BOARD_COLUMNS("board_columns/{boardId}"),
    NOTIFICATIONS("notifications"),
    ;

    fun upRoute(boardId: String): String? = when (this) {
        BOARD_COLUMNS -> boardRoute(boardId)
        else -> null
    }

    companion object {
        val START: AppDestination = MY_DAY

        val topLevel: List<AppDestination> = listOf(MY_DAY, INBOX, WORKSPACES, SETTINGS)

        fun boardRoute(boardId: String): String = "board/$boardId"

        fun boardColumnsRoute(boardId: String): String = "board_columns/$boardId"

        fun fromRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
