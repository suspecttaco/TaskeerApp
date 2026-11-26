package com.simposio.taskeer.ui.navigation

enum class AppDestination(val route: String) {
    MY_DAY("my_day"),
    HOME("home"),
    UPCOMING_7_DAYS("upcoming_7_days"),
    ALL_TASKS("all_tasks"),
    CALENDAR("calendar"),
    LIST("list/{listId}"),
    BOARD("board/{boardId}"),
    BOARD_COLUMNS("board_columns/{boardId}"),
    SEARCH("search"),
    NOTIFICATIONS("notifications"),
    WORKSPACES("workspaces"),
    WORKSPACE_INVITATIONS("workspaces/invitations"),
    WORKSPACE_MEMBERS("workspaces/{workspaceId}/members"),
    SETTINGS("settings"),
    LOGIN("login"),
    REGISTER("register"),
    VERIFY_EMAIL("verify-email"),
    RESET_PASSWORD("reset-password"),
    CHANGE_PASSWORD("change-password"),
    REGENERATE_CODES("regenerate-codes"),
    ;

    fun upRoute(boardId: String): String? = when (this) {
        BOARD_COLUMNS -> boardRoute(boardId)
        else -> null
    }

    companion object {
        val START: AppDestination = MY_DAY

        fun boardRoute(boardId: String): String = "board/$boardId"

        fun boardColumnsRoute(boardId: String): String = "board_columns/$boardId"

        fun workspaceMembersRoute(workspaceId: String): String = "workspaces/$workspaceId/members"

        fun listRoute(listId: String): String = "list/$listId"

        fun fromRoute(route: String?): AppDestination? = entries.firstOrNull { it.route == route }
    }
}
