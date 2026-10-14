package com.simposio.taskeer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simposio.taskeer.ui.boards.BoardsScreen
import com.simposio.taskeer.ui.home.HomeScreen
import com.simposio.taskeer.ui.myday.MyDayScreen
import com.simposio.taskeer.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.START.route,
        modifier = modifier,
    ) {
        composable(AppDestination.HOME.route) { HomeScreen() }
        composable(AppDestination.MY_DAY.route) { MyDayScreen() }
        composable(AppDestination.WORKSPACES.route) { BoardsScreen(viewModelFactory) }
        composable(AppDestination.SETTINGS.route) { SettingsScreen() }
    }
}
