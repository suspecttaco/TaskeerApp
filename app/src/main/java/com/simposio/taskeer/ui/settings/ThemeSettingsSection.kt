package com.simposio.taskeer.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.model.ThemeMode
import com.simposio.taskeer.domain.theme.isDynamicColorAvailable
import com.simposio.taskeer.ui.tasks.SelectField
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun ThemeSettingsSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.titleMedium,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.color_origin_dynamic), modifier = Modifier.weight(1f))
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            }
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
