package com.simposio.taskeer.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.ui.shell.AppShell
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun TaskeerApp(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    TaskeerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        AppShell(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            viewModelFactory = viewModelFactory,
            modifier = modifier,
        )
    }
}
