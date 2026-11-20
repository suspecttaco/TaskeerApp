package com.simposio.taskeer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.simposio.taskeer.ui.alltasks.AllTasksScreen
import com.simposio.taskeer.ui.board.BoardScreen
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.calendar.CalendarPlaceholderScreen
import com.simposio.taskeer.ui.columns.ColumnsScreen
import com.simposio.taskeer.ui.columns.ColumnsViewModel
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.home.HomeScreen
import com.simposio.taskeer.ui.lists.ListScreen
import com.simposio.taskeer.ui.lists.ListViewModel
import com.simposio.taskeer.ui.myday.MyDayScreen
import com.simposio.taskeer.ui.notifications.NotificationsScreen
import com.simposio.taskeer.ui.settings.SettingsScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.upcoming.UpcomingScreen
import java.time.LocalDate

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelProvider.Factory,
    swipeHint: SwipeHintState,
    onAddForDate: (LocalDate?) -> Unit,
    onDelete: (String) -> Unit,
    suggestionCount: Int,
    onOpenSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    NavHost(
        navController = navController,
        startDestination = AppDestination.START.route,
        modifier = modifier,
        enterTransition = { zoomOutEnter(animated) },
        exitTransition = { zoomOutExit(animated) },
        popEnterTransition = { zoomOutEnter(animated) },
        popExitTransition = { zoomOutExit(animated) },
    ) {
        composable(AppDestination.MY_DAY.route) {
            MyDayScreen(
                viewModelFactory = viewModelFactory,
                swipeHint = swipeHint,
                onAddTask = { onAddForDate(null) },
                onDelete = onDelete,
                suggestionCount = suggestionCount,
                onOpenSuggestions = onOpenSuggestions,
            )
        }
        composable(AppDestination.HOME.route) {
            HomeScreen(
                viewModelFactory = viewModelFactory,
                onOpenList = { listId ->
                    navController.navigate(AppDestination.listRoute(listId)) { launchSingleTop = true }
                },
                onOpenBoard = { boardId ->
                    navController.navigate(AppDestination.boardRoute(boardId)) { launchSingleTop = true }
                },
                onOpenSection = navController::navigateSection,
            )
        }
        composable(AppDestination.UPCOMING_7_DAYS.route) {
            UpcomingScreen(viewModelFactory, swipeHint, onAddForDate, onDelete)
        }
        composable(AppDestination.ALL_TASKS.route) {
            AllTasksScreen(viewModelFactory, swipeHint, onAddForDate, onDelete)
        }
        composable(AppDestination.CALENDAR.route) { CalendarPlaceholderScreen() }
        composable(
            route = AppDestination.LIST.route,
            arguments = listOf(navArgument(ListViewModel.LIST_ID_ARG) { type = NavType.StringType }),
        ) {
            ListScreen(
                viewModelFactory = viewModelFactory,
                swipeHint = swipeHint,
                onAddTask = { onAddForDate(null) },
                onListMissing = navController::navigateHome,
                onDelete = onDelete,
            )
        }
        composable(AppDestination.SETTINGS.route) { SettingsScreen(viewModelFactory) }
        composable(AppDestination.NOTIFICATIONS.route) { NotificationsScreen(viewModelFactory, onDelete) }
        composable(
            route = AppDestination.BOARD.route,
            arguments = listOf(
                navArgument(BoardViewModel.BOARD_ID_ARG) { type = NavType.StringType },
            ),
        ) {
            BoardScreen(
                viewModelFactory = viewModelFactory,
                onManageColumns = { boardId ->
                    navController.navigate(AppDestination.boardColumnsRoute(boardId))
                },
                onBoardDeleted = navController::navigateHome,
                onDelete = onDelete,
            )
        }
        composable(
            route = AppDestination.BOARD_COLUMNS.route,
            arguments = listOf(
                navArgument(ColumnsViewModel.BOARD_ID_ARG) { type = NavType.StringType },
            ),
        ) {
            ColumnsScreen(viewModelFactory, onDelete)
        }
    }
}
