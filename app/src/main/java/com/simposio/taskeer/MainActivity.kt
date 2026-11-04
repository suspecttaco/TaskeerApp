package com.simposio.taskeer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simposio.taskeer.data.reminder.NotificationLaunch
import com.simposio.taskeer.di.AppContainer
import com.simposio.taskeer.di.AppViewModelFactory
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.app.TaskeerApp
import com.simposio.taskeer.ui.navigation.shouldOpenViewer

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as TaskeerApplication).container
    }

    private val viewModelFactory: AppViewModelFactory by lazy {
        AppViewModelFactory(container)
    }

    private val appViewModel: AppViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLaunchIntent(intent, isRestored = savedInstanceState != null)
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
            val openViewerRequested by appViewModel.openViewerRequested.collectAsStateWithLifecycle()

            TaskeerApp(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                viewModelFactory = viewModelFactory,
                openViewerRequested = openViewerRequested,
                onViewerHandled = appViewModel::consumeOpenViewer,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent, isRestored = false)
    }

    private fun handleLaunchIntent(intent: Intent, isRestored: Boolean) {
        val launchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val hasExtra = intent.getBooleanExtra(NotificationLaunch.EXTRA_OPEN_VIEWER, false)
        if (shouldOpenViewer(hasExtra, isRestored, launchedFromHistory)) {
            appViewModel.requestOpenViewer()
        }
        // Se retira siempre para que ninguna reentrega del mismo Intent repita la petición.
        intent.removeExtra(NotificationLaunch.EXTRA_OPEN_VIEWER)
    }
}
