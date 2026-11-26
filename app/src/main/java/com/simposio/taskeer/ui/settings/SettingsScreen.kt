package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.ui.components.AppDivider
import com.simposio.taskeer.ui.permission.NotificationPermissionNotice
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.verifyemail.ResendVerificationViewModel

@Composable
fun SettingsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    session: SessionViewModel,
    onSignIn: () -> Unit,
    onHaveVerificationLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val resendViewModel: ResendVerificationViewModel = viewModel(factory = viewModelFactory)
    val resendState by resendViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        AccountSettingsSection(
            state = sessionState,
            resend = resendState,
            actions = AccountSectionActions(
                onSignIn = onSignIn,
                onRetry = session::retryResume,
                onSignOut = session::logout,
                onResendVerification = resendViewModel::resend,
                onHaveVerificationLink = onHaveVerificationLink,
            ),
        )
        AppDivider()
        ThemeSettingsSection(
            themeMode = state.themeMode,
            dynamicColor = state.dynamicColor,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
        )
        AppDivider()
        RegionSettingsSection(
            state = state,
            onCountryClick = { viewModel.openPicker(RegionPickerKind.COUNTRY) },
            onTimeZoneClick = { viewModel.openPicker(RegionPickerKind.TIME_ZONE) },
        )
        AppDivider()
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
    state.picker?.let { kind ->
        RegionPickerSheet(
            kind = kind,
            state = state,
            actions = RegionPickerActions(
                onQueryChange = viewModel::setQuery,
                onSelectCountry = viewModel::setCountry,
                onSelectTimeZone = viewModel::setTimeZone,
                onDismiss = viewModel::closePicker,
            ),
        )
    }
}
