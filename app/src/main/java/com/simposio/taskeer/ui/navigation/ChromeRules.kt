package com.simposio.taskeer.ui.navigation

import com.simposio.taskeer.ui.quickadd.QuickAddContext

enum class TopBarKind { SECTION, HUB, DETAIL, DETAIL_WITH_MENU }

enum class PillKind { TASK, EVENT }

data class Chrome(
    val topBar: TopBarKind,
    val showHomeButton: Boolean,
    val showPill: Boolean,
    val showSuggestions: Boolean = false,
    val pill: PillKind = PillKind.TASK,
)

fun chromeFor(destination: AppDestination): Chrome = when (destination) {
    AppDestination.MY_DAY ->
        Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true, showSuggestions = true)
    AppDestination.UPCOMING_7_DAYS,
    AppDestination.ALL_TASKS,
    AppDestination.LIST,
    -> Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true)
    AppDestination.CALENDAR ->
        Chrome(TopBarKind.SECTION, showHomeButton = true, showPill = true, pill = PillKind.EVENT)
    AppDestination.HOME -> Chrome(TopBarKind.HUB, showHomeButton = false, showPill = false)
    AppDestination.BOARD -> Chrome(TopBarKind.DETAIL_WITH_MENU, showHomeButton = true, showPill = false)
    AppDestination.BOARD_COLUMNS,
    AppDestination.NOTIFICATIONS,
    AppDestination.SETTINGS,
    AppDestination.LOGIN,
    AppDestination.REGISTER,
    AppDestination.SEARCH,
    -> Chrome(TopBarKind.DETAIL, showHomeButton = false, showPill = false)
}

enum class MenuAction { SYNC, PRINT, SHARE }

enum class MenuItem {
    CLEAR_COMPLETED,
    MY_CALENDARS,
    FILTER,
    SORT,
    SETTINGS,
    RENAME_LIST,
    DELETE_LIST,
    RENAME_BOARD,
    DELETE_BOARD,
}

data class OverflowMenu(val actions: List<MenuAction>, val items: List<MenuItem>)

private val SECTION_ACTIONS = listOf(MenuAction.SYNC, MenuAction.PRINT, MenuAction.SHARE)

private fun sectionMenu(vararg items: MenuItem) = OverflowMenu(SECTION_ACTIONS, items.toList())

fun overflowMenuFor(destination: AppDestination): OverflowMenu = when (destination) {
    AppDestination.MY_DAY -> sectionMenu(MenuItem.CLEAR_COMPLETED, MenuItem.SETTINGS)
    AppDestination.UPCOMING_7_DAYS,
    AppDestination.ALL_TASKS,
    -> sectionMenu(MenuItem.CLEAR_COMPLETED, MenuItem.FILTER, MenuItem.SORT, MenuItem.SETTINGS)
    AppDestination.CALENDAR ->
        sectionMenu(MenuItem.CLEAR_COMPLETED, MenuItem.MY_CALENDARS, MenuItem.SETTINGS)
    AppDestination.LIST ->
        sectionMenu(MenuItem.CLEAR_COMPLETED, MenuItem.RENAME_LIST, MenuItem.DELETE_LIST, MenuItem.SETTINGS)
    AppDestination.BOARD -> OverflowMenu(emptyList(), listOf(MenuItem.RENAME_BOARD, MenuItem.DELETE_BOARD))
    AppDestination.HOME,
    AppDestination.BOARD_COLUMNS,
    AppDestination.NOTIFICATIONS,
    AppDestination.SETTINGS,
    AppDestination.LOGIN,
    AppDestination.REGISTER,
    AppDestination.SEARCH,
    -> OverflowMenu(emptyList(), emptyList())
}

fun quickAddContextFor(destination: AppDestination, listId: String?): QuickAddContext = when (destination) {
    AppDestination.MY_DAY -> QuickAddContext.MyDay
    AppDestination.LIST -> listId?.let { QuickAddContext.InList(it) } ?: QuickAddContext.Default
    else -> QuickAddContext.Default
}

// La última lista no se puede eliminar: con una sola, DELETE_LIST no se ofrece.
fun canDeleteList(listCount: Int): Boolean = listCount > 1

fun OverflowMenu.withListCount(listCount: Int): OverflowMenu =
    copy(items = items.filter { it != MenuItem.DELETE_LIST || canDeleteList(listCount) })

fun OverflowMenu.withBoardPresent(present: Boolean): OverflowMenu =
    copy(items = items.filter { present || (it != MenuItem.RENAME_BOARD && it != MenuItem.DELETE_BOARD) })
