package com.simposio.taskeer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import com.simposio.taskeer.R
import com.simposio.taskeer.domain.region.RegionCatalog
import com.simposio.taskeer.ui.components.SectionHeader
import com.simposio.taskeer.ui.theme.Spacing
import com.simposio.taskeer.ui.theme.TaskeerTheme

@Composable
internal fun RegionSettingsSection(
    state: SettingsUiState,
    onCountryClick: () -> Unit,
    onTimeZoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countryLabel = stringResource(R.string.settings_region_country)
    val zoneLabel = stringResource(R.string.settings_region_time_zone)
    val countryValue = state.selectedCountry
        ?.let { "${it.flagEmoji} ${it.name}" }
        ?: stringResource(R.string.settings_region_no_country)
    val zoneValue = state.selectedTimeZone
        ?.let { "${it.option.displayName} (${it.offsetLabel})" }
        ?: stringResource(R.string.settings_region_device_zone_value, state.systemZoneId)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.ItemGap)) {
        SectionHeader(text = stringResource(R.string.settings_region_title), icon = Icons.Filled.Public)
        RegionRow(label = countryLabel, value = countryValue, onClick = onCountryClick)
        RegionRow(label = zoneLabel, value = zoneValue, onClick = onTimeZoneClick)
    }
}

@Composable
private fun RegionRow(label: String, value: String, onClick: () -> Unit) {
    val description = "$label: $value"
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

@Preview(showBackground = true)
@Composable
private fun RegionSettingsSectionPreview() {
    TaskeerTheme {
        RegionSettingsSection(
            state = SettingsUiState(
                selectedCountry = RegionCatalog.country("MX"),
                systemZoneId = "Europe/Madrid",
            ),
            onCountryClick = {},
            onTimeZoneClick = {},
        )
    }
}
