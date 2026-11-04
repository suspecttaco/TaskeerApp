package com.simposio.taskeer.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.theme.isDynamicColorAvailable
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.tasks.SelectField
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun ThemeSettingsSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.InlineGap)) {
        SectionHeader(
            text = stringResource(R.string.settings_theme_title),
            icon = Icons.Filled.Palette,
        )
        SelectField(
            label = stringResource(R.string.settings_theme_mode_label),
            options = ThemeMode.entries,
            selected = themeMode,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onThemeModeChange,
        )
        // En API < 31 no hay color dinámico: la opción no se ofrece para no dejar un ajuste muerto.
        if (isDynamicColorAvailable(Build.VERSION.SDK_INT)) {
            SwitchRow(
                icon = Icons.Filled.ColorLens,
                label = stringResource(R.string.color_origin_dynamic),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
        }
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
    ThemeMode.AUTO -> R.string.theme_mode_auto
}

@Preview(showBackground = true)
@Composable
private fun ThemeSettingsSectionPreview() {
    TaskeerTheme {
        ThemeSettingsSection(
            themeMode = ThemeMode.AUTO,
            dynamicColor = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
        )
    }
}
