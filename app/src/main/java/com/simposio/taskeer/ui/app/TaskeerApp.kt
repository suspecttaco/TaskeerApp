package com.simposio.taskeer.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simposio.taskeer.domain.auth.SessionState
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.ui.auth.SessionViewModel
import com.simposio.taskeer.ui.notifications.UnreadCounterViewModel
import com.simposio.taskeer.ui.permission.NotificationPermissionGate
import com.simposio.taskeer.ui.shell.AppShell
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
fun TaskeerApp(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    viewModelFactory: ViewModelProvider.Factory,
    openViewerRequested: Boolean,
    onViewerHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unreadCounter: UnreadCounterViewModel = viewModel(factory = viewModelFactory)
    val unreadCount by unreadCounter.unreadCount.collectAsStateWithLifecycle()
    val session: SessionViewModel = viewModel(factory = viewModelFactory)
    val sessionState by session.state.collectAsStateWithLifecycle()
    val signedIn = sessionState is SessionState.SignedIn
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Cubre la apertura en frío y cada vuelta desde segundo plano; el tope de frecuencia vive en el repositorio.
    LaunchedEffect(signedIn, lifecycle) {
        if (signedIn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { unreadCounter.onAppForegrounded() }
    }
    TaskeerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        NotificationPermissionGate(viewModelFactory)
        AppShell(
            viewModelFactory = viewModelFactory,
            unreadCount = unreadCount,
            openViewerRequested = openViewerRequested,
            onViewerHandled = onViewerHandled,
            modifier = modifier,
        )
    }
}
