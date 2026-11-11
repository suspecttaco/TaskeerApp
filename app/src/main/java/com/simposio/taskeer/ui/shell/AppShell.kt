package com.simposio.taskeer.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.notification.badgeLabel
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.components.ActionIcon
import com.simposio.taskeer.ui.components.DecorativeIcon
import com.simposio.taskeer.ui.navigation.AppDestination
import com.simposio.taskeer.ui.navigation.AppNavHost
import com.simposio.taskeer.ui.navigation.BackStep
import com.simposio.taskeer.ui.navigation.backStep
import com.simposio.taskeer.ui.navigation.topLevelNavSpec
import com.simposio.taskeer.ui.theme.TaskeerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
    openViewerRequested: Boolean = false,
    onViewerHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navSpec = topLevelNavSpec()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(currentEntry?.destination?.route)

    LaunchedEffect(openViewerRequested) {
        if (openViewerRequested) {
            navController.navigate(AppDestination.NOTIFICATIONS.route) { launchSingleTop = true }
            onViewerHandled()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            val boardId = currentEntry?.arguments?.getString(BoardViewModel.BOARD_ID_ARG)
            val back = backStep(
                destination = currentDestination,
                boardId = boardId,
                hasPrevious = navController.previousBackStackEntry != null,
            )
            TopAppBar(
                navigationIcon = {
                    if (back != BackStep.None) {
                        ActionIcon(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            onClick = {
                                when (back) {
                                    BackStep.Pop -> navController.popBackStack()
                                    is BackStep.Navigate ->
                                        navController.navigate(back.route) { launchSingleTop = true }
                                    BackStep.None -> Unit
                                }
                            },
                        )
                    }
                },
                title = {
                    Text(stringResource((currentDestination ?: AppDestination.START).labelRes()))
                },
                actions = {
                    NotificationsAction(
                        unreadCount = unreadCount,
                        onClick = {
                            navController.navigate(AppDestination.NOTIFICATIONS.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppDestination.topLevel.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navSpec.popUpToRoute) { saveState = navSpec.saveState }
                                restoreState = navSpec.restoreState
                                launchSingleTop = navSpec.launchSingleTop
                            }
                        },
                        icon = { DecorativeIcon(destination.icon()) },
                        label = { Text(stringResource(destination.labelRes())) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            viewModelFactory = viewModelFactory,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@Composable
private fun NotificationsAction(unreadCount: Int, onClick: () -> Unit) {
    val label = badgeLabel(unreadCount)
    BadgedBox(
        badge = {
            if (label != null) {
                Badge { Text(label) }
            }
        },
    ) {
        ActionIcon(
            icon = Icons.Filled.Notifications,
            contentDescription = stringResource(R.string.nav_notifications),
            onClick = onClick,
        )
    }
}

private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.MY_DAY -> Icons.Filled.WbSunny
    AppDestination.INBOX -> Icons.Filled.Inbox
    AppDestination.WORKSPACES -> Icons.Filled.Dashboard
    AppDestination.SETTINGS -> Icons.Filled.Settings
    else -> Icons.Filled.WbSunny
}

@StringRes
private fun AppDestination.labelRes(): Int = when (this) {
    AppDestination.MY_DAY -> R.string.nav_my_day
    AppDestination.INBOX -> R.string.nav_inbox
    AppDestination.WORKSPACES -> R.string.nav_workspaces
    AppDestination.SETTINGS -> R.string.nav_settings
    AppDestination.BOARD -> R.string.board_title
    AppDestination.BOARD_COLUMNS -> R.string.columns_title
    AppDestination.NOTIFICATIONS -> R.string.notifications_title
}

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    TaskeerTheme {
        AppShell(
            // La preview abre el destino inicial (Mi día): la factoría nunca se usa.
            viewModelFactory = object : ViewModelProvider.Factory {},
        )
    }
}
