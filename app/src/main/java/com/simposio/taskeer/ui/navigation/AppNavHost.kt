package com.simposio.taskeer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.simposio.taskeer.ui.alltasks.AllTasksScreen
import com.simposio.taskeer.ui.accountsecurity.ChangePasswordScreen
import com.simposio.taskeer.ui.accountsecurity.RegenerateCodesScreen
import com.simposio.taskeer.ui.auth.LoginHandoff
import com.simposio.taskeer.ui.auth.LoginNotice
import com.simposio.taskeer.ui.auth.LoginScreen
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.ui.board.BoardScreen
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.calendar.CalendarScreen
import com.simposio.taskeer.ui.columns.ColumnsScreen
import com.simposio.taskeer.ui.columns.ColumnsViewModel
import com.simposio.taskeer.ui.components.rememberAnimationsEnabled
import com.simposio.taskeer.ui.home.HomeScreen
import com.simposio.taskeer.ui.lists.ListScreen
import com.simposio.taskeer.ui.lists.ListViewModel
import com.simposio.taskeer.ui.myday.MyDayScreen
import com.simposio.taskeer.ui.notifications.NotificationsScreen
import com.simposio.taskeer.ui.passwordreset.ResetPasswordScreen
import com.simposio.taskeer.ui.register.RegisterScreen
import com.simposio.taskeer.ui.search.SearchScreen
import com.simposio.taskeer.ui.settings.SettingsScreen
import com.simposio.taskeer.ui.tasks.SwipeHintState
import com.simposio.taskeer.ui.upcoming.UpcomingScreen
import com.simposio.taskeer.ui.verifyemail.VerifyEmailScreen
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
    session: SessionViewModel,
    modifier: Modifier = Modifier,
) {
    val animated = rememberAnimationsEnabled()
    // El correo no es secreto pero no se persiste: si se pierde al rotar, el usuario lo escribe.
    var pendingLogin by remember { mutableStateOf<LoginHandoff?>(null) }
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
        composable(AppDestination.CALENDAR.route) { CalendarScreen(viewModelFactory, onDelete) }
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
        composable(AppDestination.SETTINGS.route) {
            SettingsScreen(
                viewModelFactory = viewModelFactory,
                session = session,
                onSignIn = { navController.navigate(AppDestination.LOGIN.route) { launchSingleTop = true } },
                onHaveVerificationLink = {
                    navController.navigate(AppDestination.VERIFY_EMAIL.route) { launchSingleTop = true }
                },
                onChangePassword = {
                    navController.navigate(AppDestination.CHANGE_PASSWORD.route) { launchSingleTop = true }
                },
                onRegenerateCodes = {
                    navController.navigate(AppDestination.REGENERATE_CODES.route) { launchSingleTop = true }
                },
            )
        }
        composable(AppDestination.LOGIN.route) {
            LoginScreen(
                viewModelFactory = viewModelFactory,
                onSignedIn = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(AppDestination.REGISTER.route) { launchSingleTop = true } },
                onHaveVerificationLink = {
                    navController.navigate(AppDestination.VERIFY_EMAIL.route) { launchSingleTop = true }
                },
                onRecoverAccess = {
                    navController.navigate(AppDestination.RESET_PASSWORD.route) { launchSingleTop = true }
                },
                prefill = pendingLogin,
                onPrefillConsumed = { pendingLogin = null },
            )
        }
        composable(AppDestination.REGISTER.route) {
            RegisterScreen(
                viewModelFactory = viewModelFactory,
                onFinished = { email ->
                    pendingLogin = email?.let { LoginHandoff(it, LoginNotice.ACCOUNT_CREATED) }
                    navController.navigate(AppDestination.LOGIN.route) {
                        popUpTo(AppDestination.SETTINGS.route)
                        launchSingleTop = true
                    }
                },
                onHaveAccount = { navController.popBackStack() },
            )
        }
        composable(AppDestination.RESET_PASSWORD.route) {
            ResetPasswordScreen(
                viewModelFactory = viewModelFactory,
                onReset = { email ->
                    pendingLogin = LoginHandoff(email, LoginNotice.PASSWORD_RESET)
                    navController.popBackStack(AppDestination.LOGIN.route, inclusive = false)
                },
            )
        }
        composable(AppDestination.CHANGE_PASSWORD.route) {
            ChangePasswordScreen(
                viewModelFactory = viewModelFactory,
                onDone = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(AppDestination.LOGIN.route) {
                        popUpTo(AppDestination.SETTINGS.route)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.REGENERATE_CODES.route) {
            RegenerateCodesScreen(
                viewModelFactory = viewModelFactory,
                onFinished = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(AppDestination.LOGIN.route) {
                        popUpTo(AppDestination.SETTINGS.route)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.VERIFY_EMAIL.route) {
            VerifyEmailScreen(
                viewModelFactory = viewModelFactory,
                onDone = { navController.popBackStack() },
            )
        }
        composable(AppDestination.SEARCH.route) {
            SearchScreen(
                viewModelFactory = viewModelFactory,
                onOpenBoard = { boardId ->
                    navController.navigate(AppDestination.boardRoute(boardId)) { launchSingleTop = true }
                },
                onOpenList = { listId ->
                    navController.navigate(AppDestination.listRoute(listId)) { launchSingleTop = true }
                },
                onDelete = onDelete,
            )
        }
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
                onBoardMissing = navController::navigateHome,
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
