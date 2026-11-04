package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice

@Composable
fun SettingsScreen(viewModelFactory: ViewModelProvider.Factory, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ThemeSettingsSection(
            themeMode = state.themeMode,
            dynamicColor = state.dynamicColor,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
        )
        HorizontalDivider()
        ReminderSettingsSection(
            state = state,
            actions = ReminderSettingsActions(
                onDeadlineEnabledChange = viewModel::setDeadlineEnabled,
                onLeadChange = viewModel::setLead,
                onDigestEnabledChange = viewModel::setDigestEnabled,
                onDigestDaysChange = viewModel::setDigestDays,
                onDigestTimeChange = viewModel::setDigestTime,
                onDefaultTimeChange = viewModel::setDefaultReminderTime,
            ),
            permissionNotice = { NotificationPermissionNotice(viewModelFactory) },
        )
    }
}
