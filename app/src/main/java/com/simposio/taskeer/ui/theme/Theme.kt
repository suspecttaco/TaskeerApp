package com.simposio.taskeer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.theme.ColorOrigin
import com.simposio.taskeer.domain.theme.resolveColorOrigin
import com.simposio.taskeer.domain.theme.resolveIsDark

@Composable
fun TaskeerTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveIsDark(isSystemInDarkTheme())
    val colorOrigin = resolveColorOrigin(dynamicColor, Build.VERSION.SDK_INT)

    val colorScheme = when {
        colorOrigin == ColorOrigin.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
