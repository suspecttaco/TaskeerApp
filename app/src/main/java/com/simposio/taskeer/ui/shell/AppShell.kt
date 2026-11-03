package com.simposio.taskeer.ui.shell

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.theme.isDynamicColorAvailable
import com.simposio.taskeer.ui.board.BoardViewModel
import com.simposio.taskeer.ui.navigation.AppDestination
import com.simposio.taskeer.ui.navigation.AppNavHost
import com.simposio.taskeer.ui.navigation.topLevelNavSpec
import com.simposio.taskeer.ui.theme.TaskeerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navSpec = topLevelNavSpec()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(currentEntry?.destination?.route)
    var themeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            val boardId = currentEntry?.arguments?.getString(BoardViewModel.BOARD_ID_ARG)
            val upRoute = boardId?.let { currentDestination?.upRoute(it) }
            TopAppBar(
                navigationIcon = {
                    if (upRoute != null) {
                        TextButton(
                            onClick = {
                                if (!navController.popBackStack()) {
                                    navController.navigate(upRoute) { launchSingleTop = true }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.nav_back))
                        }
                    }
                },
                title = {
                    Text(stringResource((currentDestination ?: AppDestination.START).labelRes()))
                },
                actions = {
                    Box {
                        TextButton(onClick = { themeMenuExpanded = true }) {
                            Text(stringResource(R.string.theme_action))
                        }
                        if (themeMenuExpanded) {
                            ThemeMenu(
                                themeMode = themeMode,
                                dynamicColor = dynamicColor,
                                onThemeModeChange = onThemeModeChange,
                                onDynamicColorChange = onDynamicColorChange,
                                onDismiss = { themeMenuExpanded = false },
                            )
                        }
                    }
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
                        // La barra va con etiquetas: aún no hay un juego de iconos.
                        icon = {},
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
private fun ThemeMenu(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        ThemeMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(mode.labelRes())) },
                onClick = {
                    onThemeModeChange(mode)
                    onDismiss()
                },
                leadingIcon = { if (mode == themeMode) CheckMark() },
            )
        }
        // En API < 31 no hay color dinámico: la opción no se ofrece para no dejar un ajuste muerto.
        if (isDynamicColorAvailable(Build.VERSION.SDK_INT)) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.color_origin_brand)) },
                onClick = {
                    onDynamicColorChange(false)
                    onDismiss()
                },
                leadingIcon = { if (!dynamicColor) CheckMark() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.color_origin_dynamic)) },
                onClick = {
                    onDynamicColorChange(true)
                    onDismiss()
                },
                leadingIcon = { if (dynamicColor) CheckMark() },
            )
        }
    }
}

@Composable
private fun CheckMark() {
    Text(stringResource(R.string.menu_check))
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
    ThemeMode.AUTO -> R.string.theme_mode_auto
}

@StringRes
private fun AppDestination.labelRes(): Int = when (this) {
    AppDestination.HOME -> R.string.nav_home
    AppDestination.MY_DAY -> R.string.nav_my_day
    AppDestination.WORKSPACES -> R.string.nav_workspaces
    AppDestination.SETTINGS -> R.string.nav_settings
    AppDestination.BOARD -> R.string.board_title
    AppDestination.BOARD_COLUMNS -> R.string.columns_title
}

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    TaskeerTheme {
        AppShell(
            themeMode = ThemeMode.AUTO,
            dynamicColor = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            // La preview abre el destino inicial (Inicio): la factoría nunca se usa.
            viewModelFactory = object : ViewModelProvider.Factory {},
        )
    }
}
