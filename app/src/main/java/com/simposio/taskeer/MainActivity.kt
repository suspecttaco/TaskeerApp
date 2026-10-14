package com.simposio.taskeer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simposio.taskeer.di.AppContainer
import com.simposio.taskeer.di.AppViewModelFactory
import com.simposio.taskeer.ui.app.AppViewModel
import com.simposio.taskeer.ui.app.TaskeerApp

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as TaskeerApplication).container
    }

    private val appViewModel: AppViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()

            TaskeerApp(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                onThemeModeChange = appViewModel::setThemeMode,
                onDynamicColorChange = appViewModel::setDynamicColor,
            )
        }
    }
}
